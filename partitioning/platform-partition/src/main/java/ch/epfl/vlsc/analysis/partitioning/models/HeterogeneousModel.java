package ch.epfl.vlsc.analysis.partitioning.models;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.DeviceProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;

import gurobi.*;

import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;

import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HeterogeneousModel extends MulticorePerformanceModel {




    private final DeviceProfileDataBase accelDB;
    private final Double accelClockPeriod;




    public HeterogeneousModel(CompilationTask task, Context context,
                               MulticoreProfileDataBase multicoreDB,
                               DeviceProfileDataBase accelDB,
                               Double multicoreClockPeriod,
                               Double accelClockPeriod, Double timeLimit) {
        super(task, context, multicoreDB, multicoreClockPeriod, timeLimit);

        this.accelDB = accelDB;

        this.accelClockPeriod = accelClockPeriod;

        this.context = context;
        this.task = task;

    }



    protected ImmutableList<TypedPartition> makeHeterogeneousPartitionSet(int numberOfCores) {
        ImmutableList.Builder<TypedPartition> builder = ImmutableList.builder();
        for (int i = 0 ; i < numberOfCores; i++) {
            builder.add(new SoftwarePartition(i));
        }
        builder.add(new HardwarePartition(numberOfCores));
        return builder.build();
    }

    @Override
    public ImmutableList<PartitioningSolution<String>> solveModel(int numberOfCores) {

        Network network = task.getNetwork();

        try {

            context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO,
                    "Solving performance model for " + numberOfCores));
            GRBEnv env = new GRBEnv(true);
            Path logPath = context.getConfiguration().get(Compiler.targetPath).resolve("heterogeneous");
            if (!logPath.toFile().exists()) {
                logPath.toFile().mkdirs();
            }
            Path logfile = logPath.resolve("partitions.log");

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Logging into " + logfile.toString()));

            env.set("LogFile", logfile.toString());


            env.start();

            model = new GRBModel(env);

            model.set(GRB.DoubleParam.TimeLimit, this.timeLimit);


//            int numberOfCores = context.getConfiguration().get(PartitionSettings.cpuCoreCount);
            if (numberOfCores < 1) {
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, String.format("Invalid number of cores %d, " +
                                "number of cores should be larger that 0.", numberOfCores)));
            }

            // Set of all partitions
            ImmutableList<TypedPartition> partitions = makeHeterogeneousPartitionSet(numberOfCores);

            HardwarePartition accelPartition = partitions.stream()
                    .filter(p -> p instanceof HardwarePartition)
                    .map(p -> (HardwarePartition) p)
                    .findFirst().orElseThrow(() -> new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,
                            "Hardware partition not set")));

            SoftwarePartition plinkPartition = (SoftwarePartition) partitions.get(0);



            Map<Instance, DecisionVariables> instanceDecisionVariables = network.getInstances().stream()
                    .collect(Collectors.toMap(
                    Function.identity(), instance -> new DecisionVariables(instance, partitions, model)));


            Map<Instance, GRBVar> instanceNotOnAccelDecisionVariables = makeInstanceNotOnAccelMap(accelPartition,
                    instanceDecisionVariables);

            // make sure every actor is assigned to exactly one partition
            for (Instance i: task.getNetwork().getInstances()) {
                GRBLinExpr uniquePartitionExpression = instanceDecisionVariables.get(i).getUniquePartitionConstraint();
                model.addConstr(uniquePartitionExpression, GRB.EQUAL, 1.0, i.getInstanceName() + "_unique_partition");
            }

            // Plink read/kernel/write times
            ExpressionTuple plinkReadTimeExpressionTuple = getPLinkReadTimeExpression(accelPartition, instanceDecisionVariables,
                    instanceNotOnAccelDecisionVariables);
            GRBLinExpr plinkReadTimeExpression = plinkReadTimeExpressionTuple.first;

            GRBLinExpr plinkNumberOfReadConnections = getNumberOfReadConnectionsExpression(
                    accelPartition, instanceDecisionVariables, instanceNotOnAccelDecisionVariables
            );


            ExpressionTuple plinkWriteTimeExpressionTuple = getPlinkWriteTimeExpression(accelPartition,
                    instanceDecisionVariables,
                    instanceNotOnAccelDecisionVariables);

            GRBLinExpr plinkWriteTimeExpression = plinkWriteTimeExpressionTuple.first;

            GRBLinExpr plinkNumberOfWriteConnections = getNumberOfWriteConnectionsExpression(
                    accelPartition, instanceDecisionVariables, instanceNotOnAccelDecisionVariables
            );


            // -- constraint the number of connection to and form the hardware

            GRBVar numberOfReadConnections = model.addVar(0.0, task.getNetwork().getConnections().size(),
                    0.0, GRB.INTEGER,
                    "read_connections");
            GRBVar numberOfWriteConnections = model.addVar(0.0, task.getNetwork().getConnections().size(), 0.0,
                    GRB.INTEGER, "write_connections");

            model.addConstr(numberOfReadConnections, GRB.EQUAL, plinkNumberOfReadConnections,
                    "constraint_number_of_reads");
            model.addConstr(numberOfWriteConnections, GRB.EQUAL, plinkNumberOfWriteConnections,
                    "constraint_number_of_writes");
            GRBLinExpr numberOfConnectionsExpressions = new GRBLinExpr();

            numberOfConnectionsExpressions.addTerm(1.0, numberOfReadConnections);
            numberOfConnectionsExpressions.addTerm(1.0, numberOfWriteConnections);

            model.addConstr(numberOfConnectionsExpressions, GRB.LESS_EQUAL, 15.,
                    "constraint_number_of_connections");

            GRBVar plinkKernelTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                    "t_plink_kernel");

            ImmutableList<GRBVar> hardwareTimes = getPLinkKernelTimeExpression(accelPartition,
                    instanceDecisionVariables);
            model.addGenConstrMax(plinkKernelTime, hardwareTimes.toArray(new GRBVar[0]), 0.0,
                    "constraint_t_plink_kernel");

            GRBLinExpr plinkKernelTimeExpression = new GRBLinExpr();
            plinkKernelTimeExpression.addTerm(1.0, plinkKernelTime);

            GRBVar plinkReadTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                    "t_plink_read");
            GRBVar plinkWriteTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                    "t_plink_write");



            model.addConstr(plinkReadTime, GRB.EQUAL, plinkReadTimeExpression, "constraint_t_plink_read");
            model.addConstr(plinkWriteTime, GRB.EQUAL, plinkWriteTimeExpression, "constraint_t_plink_write");

            GRBVar plinkTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                    "t_plink");

            GRBLinExpr plinkTimeExpression = new GRBLinExpr();
            plinkTimeExpression.addTerm(1.0, plinkReadTime);
            plinkTimeExpression.addTerm(1.0, plinkKernelTime);
            plinkTimeExpression.addTerm(1.0, plinkWriteTime);

            model.addConstr(plinkTime, GRB.EQUAL, plinkTimeExpression, "constraint_plink_time");


            // formulate the time spent in each partition

            ImmutableList<SoftwarePartition> softwarePartitions = partitions.stream()
                    .filter(p -> p instanceof SoftwarePartition)
                    .map(p -> (SoftwarePartition) p).collect(ImmutableList.collector());

            List<GRBVar> partitionExecTimeList =  new ArrayList<>();
            for (SoftwarePartition partition : softwarePartitions) {
                GRBLinExpr partitionTimeExpression = getPartitionTimeExpression(partition, instanceDecisionVariables,
                        task.getNetwork().getInstances());

                if (partition.equals(plinkPartition)) {
                    partitionTimeExpression.addTerm(1.0, plinkTime);
                }

                GRBVar partitionExecTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                        "T_exec_" + partition.toString());
                model.addConstr(partitionExecTime, GRB.EQUAL, partitionTimeExpression,
                        "constraint_T_exec_" + partition.toString());

                partitionExecTimeList.add(partitionExecTime);
            }

            GRBVar[] partitionExecTimeArray = partitionExecTimeList.toArray(new GRBVar[softwarePartitions.size()]);
            GRBVar executionTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "T_exec");
            model.addGenConstrMax(executionTime, partitionExecTimeArray, 0.0, "constraint_T_exec");


            // -- pin software and hardware actors
            for (Instance instance : task.getNetwork().getInstances()) {
                if (!this.accelDB.getExecutionProfileDataBase().contains(instance)) {
                    // -- instance should be software only
                    GRBVar v = instanceDecisionVariables.get(instance).getDecisionVariable(accelPartition);
                    model.addConstr(v, GRB.EQUAL, 0.0, "constraint_pinned_software_"
                            + instance.getInstanceName());
                    info("Actor " + instance.getInstanceName() + " is pinned to software");
                }
                if (!this.multicoreDB.getExecutionProfileDataBase().contains(instance)) {
                    GRBVar v =  instanceDecisionVariables.get(instance).getDecisionVariable(accelPartition);
                    model.addConstr(v, GRB.EQUAL, 1.0, "constraint_pinned_hardware_" +
                            instance.getInstanceName());
                    info("Actor " + instance.getInstanceName() + " is pinned to hardware");
                }
            }


            // -- local communication

            List<GRBVar> localCommunicationTimeList = new ArrayList<>();
            for (SoftwarePartition partition: softwarePartitions) {

                GRBLinExpr localCommunicationTimeExpression = getLocalCoreCommunicationExpression(partition,
                        instanceDecisionVariables, task.getNetwork().getConnections());
                if (partition.equals(plinkPartition)) {
                    GRBLinExpr plinkLocalCommunicationExpression =
                            getPLinkLocalCommunicationCostExpression(plinkPartition, accelPartition,
                                    instanceDecisionVariables);
                    localCommunicationTimeExpression.add(plinkLocalCommunicationExpression);
                }
                GRBVar localCommunicationTimeInPartition = model.addVar(0.0, GRB.INFINITY,
                        0.0, GRB.CONTINUOUS, "T_lc_" + partition.toString());

                model.addConstr(localCommunicationTimeInPartition, GRB.EQUAL, localCommunicationTimeExpression,
                        "T_lc_" + partition.toString() + "_constraint");

                localCommunicationTimeList.add(localCommunicationTimeInPartition);

            }

            GRBVar localCommunicationTime = model.addVar(0.0, GRB.INFINITY, 0.0,
                    GRB.CONTINUOUS, "T_lc");
            GRBVar[] localCommunicationTimeInPartitionArray = localCommunicationTimeList.toArray(
                    new GRBVar[localCommunicationTimeList.size()]);
            model.addGenConstrMax(localCommunicationTime, localCommunicationTimeInPartitionArray,
                    0.0, "T_lc_constraint");

            // -- global communications
            GRBLinExpr globalCommunicationTimeExpression =
                    getCoreToCoreCommunicationTime(softwarePartitions, instanceDecisionVariables,
                            task.getNetwork().getConnections());
            GRBLinExpr plinkGlobalCommunicationExpression =
                    getSoftwareToPLinkCommunicationCostExpression(plinkPartition, accelPartition, softwarePartitions,
                            instanceDecisionVariables);
            // -- compute and upper bound for the global communication time
            // TODO: Maybe make the bound tighter?
            GRBVar globalCommunicationTime = model.addVar(0.0, GRB.INFINITY,
                    0.0, GRB.CONTINUOUS, "T_cc");
            model.addConstr(globalCommunicationTime, GRB.EQUAL, globalCommunicationTimeExpression,
                    "T_cc_constraint");


            GRBLinExpr objectiveExpression = new GRBLinExpr();
            objectiveExpression.addTerm(1.0, executionTime);
            objectiveExpression.addTerm(1.0, localCommunicationTime);
            objectiveExpression.addTerm(1.0, globalCommunicationTime);

            GRBVar totalTime = model.addVar(0.0,
                    GRB.INFINITY,
                    0.0, GRB.CONTINUOUS, "T");
            model.addConstr(totalTime, GRB.EQUAL, objectiveExpression, "constraint_total_time");

            Path modelFile = logPath.resolve("heterogeneous_model.lp");
            info("Writing model into " + modelFile.toAbsolutePath().toString());

            model.write(modelFile.toString());

            model.setObjective(objectiveExpression, GRB.MINIMIZE);

            model.presolve();
            model.optimize();;

            ImmutableList<PartitioningSolution<Instance>> solutions =
                    collectSolution(model, partitions, instanceDecisionVariables);
            File dumpDir = logPath.resolve(String.valueOf(numberOfCores)).toFile();
            if (!dumpDir.exists()) {
                dumpDir.mkdirs();
            }
            info("Solved the heterogeneous model for " + numberOfCores + " cores");



            ImmutableList<PartitioningSolution<String>> rawSoftwareSolutions = solutions.stream().map(sol -> {
               ImmutableList<Partition<String>> swPartitions =
                       sol.getPartitions().stream()
                               .filter(p -> p.getPartitionType() instanceof SoftwarePartition)
                               .map(p -> {
                                   if (p.getPartitionType().toIndex() == 0)
                                       return new Partition<String>
                                               (
                                                       ImmutableList.concat(
                                                               p.getInstances().map(Instance::getInstanceName),
                                                               ImmutableList.of("system_plink_0")),
                                                       p.getPartitionType()
                                               );
                                   else
                                       return new Partition<String>
                                           (p.getInstances().map(Instance::getInstanceName), p.getPartitionType());
                               })
                       .collect(ImmutableList.collector());
               return new PartitioningSolution<String>(swPartitions);
            }).collect(ImmutableList.collector());


            info("Saving multicore configs into " + dumpDir);
            File multicoreDumpDir = new File(dumpDir + "/multicore");
            multicoreDumpDir.mkdirs();
            for (PartitioningSolution<String> sol : rawSoftwareSolutions) {
                dumpMulticoreConfig(multicoreDumpDir + "/config_" + rawSoftwareSolutions.indexOf(sol) + ".xml",
                        sol, multicoreDB);

            }
            info("Saving xcf configurations into " + dumpDir);
            File xcfDumpDir = new File(dumpDir + "/xcf");
            xcfDumpDir.mkdirs();
            Map<Connection, Integer> bufferDepth = task.getNetwork().getConnections().stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            c -> this.multicoreDB.getConnectionSettingsDataBase().get(c).getDepth()
                    ));
            for (PartitioningSolution<Instance> sol : solutions) {
                dumpXcfConfig(xcfDumpDir + "/configuration_" + solutions.indexOf(sol) + ".xcf", sol, bufferDepth);
            }


            solutionsSummary(dumpDir);

            return rawSoftwareSolutions;

        } catch (GRBException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format("GRB exception caught with error %s. %s",
                                    e.getErrorCode(), e.getMessage())));

        }

    }


    protected ImmutableList<GRBVar> getPLinkKernelTimeExpression(HardwarePartition accelPartition, Map<Instance, DecisionVariables> instanceDecisionVariableMap) {

        GRBLinExpr expr = new GRBLinExpr();
        try {
            int numActors = task.getNetwork().getInstances().size();
            GRBVar[] variables = task.getNetwork().getInstances().map(instanceDecisionVariableMap::get)
                    .stream()
                    .map(vars -> vars.getDecisionVariable(accelPartition))
                    .collect(Collectors.toList())
                    .toArray(new GRBVar[numActors]);


            double upperBound = task.getNetwork().getInstances().stream().map(
                    i -> this.accelDB.getInstanceTicks(i).doubleValue()
            ).reduce(Double::sum).orElseThrow(
                    () -> new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not compute " +
                            "t_plink upper bound"))) * this.accelClockPeriod;

            ImmutableList.Builder<GRBVar> builder = ImmutableList.builder();
            for (int ix = 0; ix < numActors; ix++) {
                Instance inst = task.getNetwork().getInstances().get(ix);
                String varName = "t_hw_" + inst.getInstanceName();
                GRBVar identityVar = model.addVar(0.0, upperBound, 0.0, GRB.CONTINUOUS, varName);
                GRBLinExpr tmp = new GRBLinExpr();
                double execTime = this.accelClockPeriod * this.accelDB.getInstanceTicks(inst);
                tmp.addTerm(execTime, variables[ix]);

                model.addConstr(identityVar, GRB.EQUAL, tmp, "constraint_" + varName);

                builder.add(identityVar);

            }

            return builder.build();

        } catch (GRBException e) {
            fatalError("could build plink kernel time expression");
        }
        return ImmutableList.empty();
    }

    public static class ExpressionTuple {
        public final GRBLinExpr first;
        public final GRBLinExpr second;
        private ExpressionTuple(GRBLinExpr first, GRBLinExpr second) {
            this.first = first;
            this.second = second;
        }
        static public  ExpressionTuple of(GRBLinExpr first, GRBLinExpr second) {
            return new ExpressionTuple(first, second);
        }
    }

    /**
     * Forumulates an expression for PLINK read cost
     * @param accelPartition
     * @param instanceDecisionVariableMap
     * @param instanceOnAccelVariables
     * @return
     */
    protected ExpressionTuple getPLinkReadTimeExpression(HardwarePartition accelPartition,
                                                     Map<Instance, DecisionVariables> instanceDecisionVariableMap,
                                                     Map<Instance, GRBVar> instanceOnAccelVariables) {
        GRBLinExpr expr = new GRBLinExpr();
        GRBLinExpr connectionCount = new GRBLinExpr();
        try {

            for (Connection connection : task.getNetwork().getConnections()) {

                Instance sourceInstance = findInstance(connection.getSource());
                Instance targetInstance = findInstance(connection.getTarget());

                GRBVar sourceInstanceDecisionVariable = instanceDecisionVariableMap.get(sourceInstance)
                        .getDecisionVariable(accelPartition);

                GRBVar targetNotOnAccelVariable = instanceOnAccelVariables.get(targetInstance);

                String hardwareSoftwareConnectionVariableName = getConnectionName(connection) + "_" +
                        accelPartition.toString() + "_not_" + accelPartition.toString();
                GRBVar hardwareSoftwareConnectionVariable = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        hardwareSoftwareConnectionVariableName);
                GRBVar[] conjunctionArgs = {
                    sourceInstanceDecisionVariable,
                    targetNotOnAccelVariable
                };

                model.addGenConstrAnd(hardwareSoftwareConnectionVariable, conjunctionArgs, "constraint_" +
                        hardwareSoftwareConnectionVariableName);

                Long bufferSizeBytes = Long.valueOf((this.multicoreDB.getConnectionBytes(connection)));
                Long byteExchanged = this.multicoreDB.getBytesExchanged(connection);
                Double readTime = this.accelDB.getPCIeReadTime(bufferSizeBytes, byteExchanged) * 1e-9;
                expr.addTerm(readTime, hardwareSoftwareConnectionVariable);
                connectionCount.addTerm(1.0, hardwareSoftwareConnectionVariable);
            }


        } catch (GRBException e) {
            fatalError("Could not build plink write cost expression: " + e.getMessage());
        }

        return ExpressionTuple.of(expr, connectionCount);
    }


    /**
     * Formulates the PLINK write time
     * @param accelPartition
     * @param instanceDecisionVariableMap
     * @param instanceNotOnAccelVariables
     * @return
     */
    protected ExpressionTuple getPlinkWriteTimeExpression(HardwarePartition accelPartition,
                                                     Map<Instance, DecisionVariables> instanceDecisionVariableMap,
                                                     Map<Instance, GRBVar> instanceNotOnAccelVariables) {
        GRBLinExpr expr = new GRBLinExpr();
        GRBLinExpr connectionCount = new GRBLinExpr();
        try {
            for (Connection connection : task.getNetwork().getConnections()) {
                Instance sourceInstance = findInstance(connection.getSource());
                Instance targetInstance = findInstance(connection.getTarget());

                GRBVar sourceInstanceNotOnAccelDecisionVariable = instanceNotOnAccelVariables.get(sourceInstance);
                GRBVar targetInstanceOnAccelDecisionVariable = instanceDecisionVariableMap.get(targetInstance)
                        .getDecisionVariable(accelPartition);
                String hardwareSoftwareConnectionVariableName = getConnectionName(connection) + "_not_" +
                        accelPartition.toString() + "_" + accelPartition.toString();

                GRBVar hardwareSoftwareConnectionVariable = model.addVar(
                        0.0, 1.0, 0.0, GRB.BINARY, hardwareSoftwareConnectionVariableName
                );
                GRBVar[] conjunctionArgs = {
                        sourceInstanceNotOnAccelDecisionVariable, targetInstanceOnAccelDecisionVariable
                };
                model.addGenConstrAnd(hardwareSoftwareConnectionVariable, conjunctionArgs,
                        "constraint_" + hardwareSoftwareConnectionVariableName);
                Long bufferSizeBytes = Long.valueOf(this.multicoreDB.getConnectionBytes(connection));
                Long bytesExchanged = this.multicoreDB.getBytesExchanged(connection);
                Double writeTime = this.accelDB.getPCIeWriteTime(bufferSizeBytes, bytesExchanged) * 1e-9;

                expr.addTerm(writeTime, hardwareSoftwareConnectionVariable);
                connectionCount.addTerm(1.0, hardwareSoftwareConnectionVariable);
            }
        } catch (GRBException e) {
            fatalError("Could not build the plink write time expression: " + e.getMessage());
        }
        return ExpressionTuple.of(expr, connectionCount);
    }

    /**
     * Create a mapping from instances to decision variables indicating that the instance is not
     * on the accelerator partition
     * @param accelPartition
     * @param instanceDecisionVariableMap
     * @return
     */
    protected Map<Instance, GRBVar> makeInstanceNotOnAccelMap(HardwarePartition accelPartition,
                                                              Map<Instance, DecisionVariables>
                                                                      instanceDecisionVariableMap) {

        Map<Instance, GRBVar> mapping = new HashMap<>();

        try {
            for (Instance instance : task.getNetwork().getInstances()) {

                GRBVar instanceOnAccelDecisionVariable = instanceDecisionVariableMap.get(instance)
                        .getDecisionVariable(accelPartition);
                String varName = "d_not_" + instance.getInstanceName() + "_accel";
                GRBVar instanceNotOnAccelDecisionVariable = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        varName);

                GRBLinExpr expr = new GRBLinExpr();
                expr.addConstant(1.0);
                expr.addTerm(-1.0, instanceOnAccelDecisionVariable);

                model.addConstr(instanceNotOnAccelDecisionVariable, GRB.EQUAL, expr, "constraint_" + varName);

                mapping.put(instance, instanceNotOnAccelDecisionVariable);
            }
        } catch (GRBException e) {

            fatalError("Could not build instances not on accel mappings: "  + e.getMessage());

        }

        return mapping;
    }

    /**
     * Formulates and expression representing the number of write connections to the hardware
     * @param accelPartition
     * @param instanceDecisionVariablesMap
     * @param instanceNotOnAccelMap
     * @return
     */
    protected GRBLinExpr getNumberOfWriteConnectionsExpression(
            HardwarePartition accelPartition,
            Map<Instance, DecisionVariables> instanceDecisionVariablesMap,
            Map<Instance, GRBVar> instanceNotOnAccelMap) {

        return getNumberOfPCIeConnectionsExpression(accelPartition, instanceDecisionVariablesMap,
                instanceNotOnAccelMap, false);
    }

    /**
     * Formulate an expression representing the number of read connection from hardware to software.
     * @param accelPartition
     * @param instanceDecisionVariablesMap
     * @param instanceNotOnAccelMap
     * @return
     */
    protected GRBLinExpr getNumberOfReadConnectionsExpression(
            HardwarePartition accelPartition,
            Map<Instance, DecisionVariables> instanceDecisionVariablesMap,
            Map<Instance, GRBVar> instanceNotOnAccelMap) {
        return getNumberOfPCIeConnectionsExpression(
                accelPartition, instanceDecisionVariablesMap, instanceNotOnAccelMap, true);

    }

    private GRBLinExpr getNumberOfPCIeConnectionsExpression(
            HardwarePartition accelPartition,
            Map<Instance, DecisionVariables> instanceDecisionVariablesMap,
            Map<Instance, GRBVar> instanceNotOnAccelMap,
            boolean is_read) {

        GRBLinExpr expr = new GRBLinExpr();

        for (Instance sourceInstance : task.getNetwork().getInstances()) {


            // -- output connections for the source instance
            ImmutableList<Connection> outputConnections =
                    task.getNetwork().getConnections().stream()
                            .filter(c -> c.getSource().getInstance().orElseThrow(
                                    () -> new CompilationException(
                                            new Diagnostic(
                                                    Diagnostic.Kind.ERROR,
                                                    "Encountered connection with not source instance")))
                                    .equals(sourceInstance.getInstanceName()))
                            .collect(ImmutableList.collector());
            List<String> outputPorts = new ArrayList<>();
            for (Connection c: outputConnections) {
                if (!outputPorts.contains(c.getSource().getPort()))
                    outputPorts.add(c.getSource().getPort());
            }
            // now we have list of output port with possible fan out patterns, we would like to model
            // all the connections in a fan out from sourceInstance.outputPort_i to multiple target instances as
            // a single connection to hardware. To do so, we can introduce new decision variable
            // d_write_connection_sourceInstance_outputport_i =
            // (d_sourceInstance_not_accel & d_targetInstance_0_accel) |
            // (d_sourceInstance_not_accel & d_targetInstance_1_accel) |
            // ...
            // where targetInstance_j is a consumer for outputport_i

            GRBVar sourceDecisionVariable = is_read ?
                    instanceDecisionVariablesMap.get(sourceInstance).getDecisionVariable(accelPartition) :
                    instanceNotOnAccelMap.get(sourceInstance);

            for (String output : outputPorts) {

                List<GRBVar> conjunctions = outputConnections.stream()
                        .filter(c -> c.getSource().getPort().equals(output))
                        .map(c -> {
                            Instance targetInstance = findInstance(c.getTarget());
                            GRBVar targetDecisionVariable = is_read ?
                                    instanceNotOnAccelMap.get(targetInstance) :
                                    instanceDecisionVariablesMap.get(targetInstance)
                                            .getDecisionVariable(accelPartition);
                            String conjunctionName = (is_read ? "d_read_connection_" : "d_write_connection_") +
                                    getConnectionName(c) + (is_read ? "_accel_not_accel" : "_not_accel_accel");
                            return makeConjunction(sourceDecisionVariable, targetDecisionVariable, conjunctionName);
                        }).collect(Collectors.toList());
                String disjunctionName = (is_read ? "d_read_connection_" : "d_write_connection_")
                        + sourceInstance.getInstanceName() + "_" + output;
                try {

                    GRBVar disjunction = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, disjunctionName);
                    if (conjunctions.size() >= 2) {

                        GRBVar[] args = conjunctions.toArray(new GRBVar[0]);
                        model.addGenConstrOr(disjunction, args, "disjunction_constraint_" + disjunctionName);

                    } else {
                        model.addConstr(disjunction, GRB.EQUAL, conjunctions.get(0),
                                "disjunction_constraint_" + disjunctionName);
                    }

                    // total number of write connections (excluding fanouts) is then the some of all the fanout-excluded
                    // write connections
                    expr.addTerm(1.0, disjunction);
                } catch (GRBException e) {
                    fatalError("Could not build the disjunction for " + (is_read ? "read" : "write" ) + " connection "
                            + sourceInstance.getInstanceName() + "_" + output + ":" + e.getMessage());
                }

            }

        }
        return  expr;
    }

    protected GRBVar makeConjunction(GRBVar v1, GRBVar v2,  String name) {

        GRBVar conjunction = null;
        try {
            conjunction = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, name);
            GRBVar [] args = {v1, v2};
            model.addGenConstrAnd(conjunction, args, "constraint_and_" + name);

        } catch (GRBException e) {
            fatalError("could not build conjunction between " + name + ": " + e.getMessage());
        }
        return conjunction;
    }

    /**
     * Formulates the plink to other software partitions communication cost
     * @param plinkPartition
     * @param accelPartition
     * @param softwarePartitions
     * @param instanceDecisionVariablesMap
     * @return
     */
    protected GRBLinExpr getSoftwareToPLinkCommunicationCostExpression(
            SoftwarePartition plinkPartition,
            HardwarePartition accelPartition,
            ImmutableList<SoftwarePartition> softwarePartitions,
            Map<Instance, DecisionVariables> instanceDecisionVariablesMap){

        ImmutableList<SoftwarePartition> nonPlinkPartitions = softwarePartitions.stream().filter(p -> !p.equals(plinkPartition))
                .collect(ImmutableList.collector());
        GRBLinExpr expr = new GRBLinExpr();
        for (SoftwarePartition p: nonPlinkPartitions) {

            // outbound plink to other partitions
            for (Connection connection : task.getNetwork().getConnections()) {
                Instance targetActor = findInstance(connection.getTarget());
                Instance sourceActor = findInstance(connection.getSource());
                double communicationTime = this.multicoreDB.getCommunicationTicks(connection,
                        CommonProfileDataBase.CommunicationTicks.Kind.Global) * this.multicoreClockPeriod;
                try{
                    {
                        // outbound
                        GRBVar targetDecisionVariable = instanceDecisionVariablesMap.get(targetActor)
                                .getDecisionVariable(accelPartition);
                        GRBVar sourceActorDecisionVariable =
                                instanceDecisionVariablesMap.get(sourceActor).getDecisionVariable(p);
                        String varName = getConnectionName(connection) + "_" + p.toString() + "_" +
                                accelPartition.toString();

                        GRBVar conjunctionVariable = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                varName);
                        GRBVar[] args = {
                                sourceActorDecisionVariable, targetDecisionVariable
                        };
                        model.addGenConstrAnd(conjunctionVariable, args, "constraint_" + varName);

                        expr.addTerm(communicationTime, conjunctionVariable);
                    }
                    {
                        // inbound
                        GRBVar targetDecisionVariable = instanceDecisionVariablesMap.get(targetActor)
                                .getDecisionVariable(p);
                        GRBVar sourceActorDecisionVariable = instanceDecisionVariablesMap.get(sourceActor)
                                .getDecisionVariable(accelPartition);

                        String varName = getConnectionName(connection) + "_" + accelPartition.toString() + "_" +
                                p.toString();

                        GRBVar conjunctionVariable = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                varName);
                        GRBVar[] args = {
                                sourceActorDecisionVariable, targetDecisionVariable
                        };
                        model.addGenConstrAnd(conjunctionVariable, args, "constraint_" + varName);
                        expr.addTerm(communicationTime, conjunctionVariable);
                    }

                } catch (GRBException e) {
                    fatalError("Could not build the plink to software partition cost: " + e.getMessage());
                }

            }

        }
        return  expr;
    }

    protected GRBLinExpr getPLinkLocalCommunicationCostExpression(
            SoftwarePartition plinkPartition,
            HardwarePartition accelPartition,
            Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {

        GRBLinExpr expr = new GRBLinExpr();

        for (Connection connection : task.getNetwork().getConnections()) {

            Instance targetActor = findInstance(connection.getTarget());
            Instance sourceActor = findInstance(connection.getSource());

            double communicationTime = this.multicoreDB.getCommunicationTicks(connection,
                    CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod;

            try {
                {
                    // inbound
                    GRBVar targetDecisionVar = instanceDecisionVariablesMap.get(targetActor)
                            .getDecisionVariable(accelPartition);
                    GRBVar sourceDecisionVar = instanceDecisionVariablesMap.get(sourceActor)
                            .getDecisionVariable(plinkPartition);
                    String varName = getConnectionName(connection) + "_" + plinkPartition.toString() + "_" +
                            accelPartition.toString();
                    GRBVar conjunction = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, varName);
                    GRBVar[] args = {
                            sourceDecisionVar, targetDecisionVar
                    };
                    model.addGenConstrAnd(conjunction, args, "constraint_" + varName);
                    expr.addTerm(communicationTime, conjunction);

                }
                {
                    // outbound
                    GRBVar targetDecisionVariable = instanceDecisionVariablesMap.get(targetActor)
                            .getDecisionVariable(plinkPartition);
                    GRBVar sourceDecisionVariable = instanceDecisionVariablesMap.get(sourceActor)
                            .getDecisionVariable(accelPartition);
                    String varName = getConnectionName(connection) + "_" + accelPartition.toString() + "_" +
                            plinkPartition.toString();
                    GRBVar conjunction = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, varName);
                    GRBVar[] args = {
                            sourceDecisionVariable, targetDecisionVariable
                    };
                    model.addGenConstrAnd(conjunction, args, "constraint_" + varName);
                    expr.addTerm(communicationTime, conjunction);
                }
            } catch(GRBException e) {
                fatalError("Could not formulate the plink local communication cost: " + e.getMessage());
            }

        }
        return expr;
    }


    private ImmutableList<PartitioningSolution<Instance>> collectSolution(
            GRBModel model,
            ImmutableList<TypedPartition> partitions,
            Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {
        ImmutableList.Builder<PartitioningSolution<Instance>> solutionBuilder = ImmutableList.builder();

        if (model == null) {
            return ImmutableList.empty();
        }

        try {
            int solutionCount = model.get(GRB.IntAttr.SolCount);

            for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
                model.set(GRB.IntParam.SolutionNumber, solutionIndex);
                solutionBuilder.add(createSolution(partitions, instanceDecisionVariablesMap));

            }

        } catch (GRBException e) {
            fatalError("Could not obtains solutions: " + e.getMessage());

        }

        return  solutionBuilder.build();

    }

    private PartitioningSolution<Instance> createSolution(
            ImmutableList<TypedPartition> partitions, Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {

        List<List<Instance>> builder = new ArrayList<>();

        for (TypedPartition p: partitions) {
            builder.add(new ArrayList<>());
        }


        for (Instance instance : instanceDecisionVariablesMap.keySet()) {

            int bestPartitionIndex = 0;
            double bestValue = 0;

            for (TypedPartition p: partitions) {

                GRBVar decisionVariable = instanceDecisionVariablesMap.get(instance).getDecisionVariable(p);
                try {
                    double decisionValue = decisionVariable.get(GRB.DoubleAttr.Xn);
                    if (decisionValue > bestValue) {
                        bestPartitionIndex = p.toIndex();
                        bestValue = decisionValue;
                    }
                } catch (GRBException e) {
                    fatalError("Could not extract decision variable: " + e.getMessage());
                }

            }

            builder.get(bestPartitionIndex).add(instance);

        }
        ImmutableList.Builder<Partition<Instance>> result = ImmutableList.builder();
        for (int ix = 0; ix < partitions.size(); ix++){

            result.add(new Partition<Instance>(ImmutableList.from(builder.get(ix)), partitions.get(ix)));
        }

        return new PartitioningSolution<>(result.build());
    }



    @Override
    public void solutionsSummary(File dumpDir) {
        if (model == null)
            return;
        File dumpFile = new File(dumpDir + "/solutions.csv");
        try {
            PrintWriter solutionWriter = new PrintWriter(dumpFile);
            ImmutableList<String> variables = ImmutableList.of(
                    "T", "T_exec",
                    "T_lc","T_cc",
                    "t_plink",
                    "t_plink_read",
                    "t_plink_kernel",
                    "t_plink_write", "read_connections", "write_connections");

            solutionWriter.println(String.join(",", variables));
            try {
                int solutionCount = model.get(GRB.IntAttr.SolCount);
                for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex ++) {

                    model.set(GRB.IntParam.SolutionNumber, solutionIndex);
                    System.out.println("Solution " + solutionIndex + ": ");
                    printTimingBreakdown(solutionIndex, solutionWriter, variables);
                    System.out.println();

                }
            } catch (GRBException e) {
                fatalError("Could not print solution summary: " + e.getMessage());
            }
            solutionWriter.close();
        } catch (FileNotFoundException e) {
            fatalError("Could not print solution summaries: " + e.getMessage());
        }
    }

    public void printTimingBreakdown(int id, PrintWriter writer, ImmutableList<String> variables){

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        System.out.println("Solution " + id + " : ");
        for (String varName : variables) {
            try {
                GRBVar variable = model.getVarByName(varName);
                try {

                    Double value = variable.get(GRB.DoubleAttr.Xn);
                    System.out.printf("%s = %6.6f s\n", varName, value);
                    builder.add(String.valueOf(value));
                } catch (NullPointerException e) {
                    fatalError("Could not get the value for " + varName + ": " + e.getMessage());
                }
            } catch (GRBException e) {
                fatalError("Could not get the variable " + varName + ": "  + e.getMessage());
            }

        }

        writer.println(String.join(",", builder.build()));

    }
}

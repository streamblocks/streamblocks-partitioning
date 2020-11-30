package ch.epfl.vlsc.analysis.partitioning.models;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.DeviceProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.phase.PartitioningAnalysisPhase;
import ch.epfl.vlsc.compiler.PartitionedCompilationTask;
import ch.epfl.vlsc.configuration.Configuration;
import com.sun.imageio.plugins.common.ImageUtil;
import gurobi.*;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.entity.nl.InstanceDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;


import java.lang.reflect.Type;
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
            Path logPath = context.getConfiguration().get(Compiler.targetPath);
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

            Set<String> P = new HashSet<>();
            // (Set) of all instances
            ImmutableList<Instance> I = network.getInstances();
            // (Set) of all connections
            ImmutableList<Connection> C = network.getConnections();

            Map<Instance, DecisionVariables> instanceDecisionVariables = network.getInstances().stream()
                    .collect(Collectors.toMap(
                    Function.identity(), instance -> new DecisionVariables(instance, partitions, model)));


            Map<Instance, GRBVar> instanceNotOnAccelDecisionVariables = makeInstanceNotOnAccelMap(accelPartition,
                    instanceDecisionVariables);

            // make sure every actor is assigned to exactly one partition
            for (Instance i: I) {
                GRBLinExpr uniquePartitionExpression = instanceDecisionVariables.get(i).getUniquePartitionConstraint();
                model.addConstr(uniquePartitionExpression, GRB.EQUAL, 1.0, i.getInstanceName() + "_unique_partition");
            }

            // Plink read/kernel/write times
            GRBLinExpr plinkReadTimeExpression = getPLinkReadTimeExpression(accelPartition, instanceDecisionVariables,
                    instanceNotOnAccelDecisionVariables);
            GRBLinExpr plinkKernelTimeExpression = getPLinkKernelTimeExpression(accelPartition,
                    instanceDecisionVariables);
            GRBLinExpr plinkWriteTimeExpression = getPlinkWriteTimeExpression(accelPartition,
                    instanceDecisionVariables,
                    instanceNotOnAccelDecisionVariables);

            GRBVar plinkKernelTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                    "t_plink_kernel");
            GRBVar plinkReadTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                    "t_plink_read");
            GRBVar plinkWriteTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                    "t_plink_write");

            model.addConstr(plinkKernelTime, GRB.EQUAL, plinkKernelTimeExpression, "constraint_t_plink_kernel");
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

            model.presolve();
            model.optimize();;

            return null;


        } catch (GRBException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format("GRB exception caught with error %s. %s",
                                    e.getErrorCode(), e.getMessage())));

        }
        return null;
    }


    protected GRBLinExpr getPLinkKernelTimeExpression(HardwarePartition accelPartition, Map<Instance, DecisionVariables> instanceDecisionVariableMap) {

        GRBLinExpr expr = new GRBLinExpr();
        try {
            int numActors = task.getNetwork().getInstances().size();
            GRBVar[] variables = task.getNetwork().getInstances().map(instanceDecisionVariableMap::get)
                    .stream()
                    .map(vars -> vars.getDecisionVariable(accelPartition))
                    .collect(Collectors.toList())
                    .toArray(new GRBVar[numActors]);

            double[] execTimes = new double[numActors];

            for (int ix = 0; ix < numActors; ix++) {
                execTimes[ix] = this.accelDB.getInstanceTicks(task.getNetwork().getInstances().get(ix)).doubleValue() *
                        this.accelClockPeriod;
            }

            expr.addTerms(execTimes, variables);
        } catch (GRBException e) {
            fatalError("could build plink kernel time expression");
        }
        return expr;
    }

    /**
     * Forumulates an expression for PLINK read cost
     * @param accelPartition
     * @param instanceDecisionVariableMap
     * @param instanceOnAccelVariables
     * @return
     */
    protected GRBLinExpr getPLinkReadTimeExpression(HardwarePartition accelPartition,
                                                     Map<Instance, DecisionVariables> instanceDecisionVariableMap,
                                                     Map<Instance, GRBVar> instanceOnAccelVariables) {
        GRBLinExpr expr = new GRBLinExpr();
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
                Double readTime = this.accelDB.getPCIeReadTime(bufferSizeBytes, byteExchanged);
                expr.addTerm(readTime, hardwareSoftwareConnectionVariable);

            }


        } catch (GRBException e) {
            fatalError("Could not build plink write cost expression: " + e.getMessage());
        }

        return expr;
    }

    /**
     * Formulates the PLINK write time
     * @param accelPartition
     * @param instanceDecisionVariableMap
     * @param instanceNotOnAccelVariables
     * @return
     */
    protected GRBLinExpr getPlinkWriteTimeExpression(HardwarePartition accelPartition,
                                                     Map<Instance, DecisionVariables> instanceDecisionVariableMap,
                                                     Map<Instance, GRBVar> instanceNotOnAccelVariables) {
        GRBLinExpr expr = new GRBLinExpr();

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
                Double writeTime = this.accelDB.getPCIeWriteTime(bufferSizeBytes, bytesExchanged);

                expr.addTerm(writeTime, hardwareSoftwareConnectionVariable);
            }
        } catch (GRBException e) {
            fatalError("Could not build the plink write time expression: " + e.getMessage());
        }
        return  expr;
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




}

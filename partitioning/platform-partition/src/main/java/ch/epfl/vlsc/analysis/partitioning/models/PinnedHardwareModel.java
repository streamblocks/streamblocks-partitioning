package ch.epfl.vlsc.analysis.partitioning.models;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.DeviceProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import ch.epfl.vlsc.compiler.PartitionedCompilationTask;

import gurobi.*;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.ToolAttribute;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.compiler.Compiler;



import java.nio.file.Path;
import java.util.*;
import java.util.List;

import java.util.function.Function;
import java.util.stream.Collectors;


public class PinnedHardwareModel extends PerformanceModel {


    private final MulticoreProfileDataBase multicoreDB;
    private final DeviceProfileDataBase accelDB;
    private final Double multicoreClockPeriod;
    private final Double accelClockPeriod;
    private final Double timeLimit;
    private final ImmutableList<Instance> softwareActors;
    private final ImmutableList<Instance> hardwareActors;

    public PinnedHardwareModel(CompilationTask task, Context context,
                               MulticoreProfileDataBase multicoreDB,
                               DeviceProfileDataBase accelDB,
                               Double multicoreClockPeriod,
                               Double accelClockPeriod, Double timeLimit) {
        this.multicoreDB = multicoreDB;
        this.accelDB = accelDB;
        this.multicoreClockPeriod = multicoreClockPeriod;
        this.accelClockPeriod = accelClockPeriod;
        this.timeLimit = timeLimit;
        this.context = context;
        this.task = task;


        this.softwareActors = task.getNetwork().getInstances().stream()
                .filter( i -> getInstancePartitionKind(i) == PartitionedCompilationTask.PartitionKind.SW)
                .collect(ImmutableList.collector());

        this.hardwareActors = task.getNetwork().getInstances().stream()
                .filter(i -> getInstancePartitionKind(i) == PartitionedCompilationTask.PartitionKind.HW)
                .collect(ImmutableList.collector());

    }


    private void info(String message) {
        context.getReporter().report(
                new Diagnostic(Diagnostic.Kind.INFO, message)
        );
    }
    private void error(String message) {
        context.getReporter().report(
                new Diagnostic(Diagnostic.Kind.ERROR, message)
        );
    }
    private void fatalError(String message) {
        throw new CompilationException(new Diagnostic(
                Diagnostic.Kind.ERROR, message
        ));
    }
    private PartitionedCompilationTask.PartitionKind getInstancePartitionKind(Instance instance) {
        PartitionedCompilationTask.PartitionKind defaultPartition = PartitionedCompilationTask.PartitionKind.SW;
        ImmutableList<ToolAttribute> pattrs =
                ImmutableList.from(instance.getAttributes()
                        .stream().filter(a -> a.getName().equals("partition")).collect(Collectors.toList()));
        if (pattrs.size() == 0) {
            return defaultPartition;
        } else if (pattrs.size() == 1) {
            if (pattrs.get(0) instanceof ToolValueAttribute) {
                ToolValueAttribute attr = (ToolValueAttribute) pattrs.get(0);
                String p = ((ExprLiteral) attr.getValue()).asString().get();
                return (p.equals("hw") ?
                        PartitionedCompilationTask.PartitionKind.HW : PartitionedCompilationTask.PartitionKind.SW);
            } else {
                error("partition attribute of instance " +
                                        instance.getInstanceName() + "should be a value not a type");
                return PartitionedCompilationTask.PartitionKind.SW;
            }
        } else {
            info("using default sw partition for " + instance.getInstanceName());
            return defaultPartition;
        }
    }

    private ImmutableList<SoftwarePartition> makePartitionSet(int numberOfCores) {

        ImmutableList.Builder<SoftwarePartition> builder = ImmutableList.builder();

        for (int i = 0; i < numberOfCores; i++) {
            builder.add(new SoftwarePartition(i));
        }

        return builder.build();

    }

    /**
     * Creates a unique name for an instance based on the given baseName
     * @param network the network of instances
     * @param baseName the baseName for the instance, if there are already uses of this baseName, baneName_i is
     *                 returned
     * @return a unique name
     */
    public static String uniquePlinkName(Network network, String baseName) {
        Set<String> names = network.getInstances().stream().map(Instance::getInstanceName)
                .filter(n -> n.equals(baseName))
                .collect(Collectors.toSet());
        return baseName + "_" + names.size();

    }

    /**
     * A class holding decision variables per instance, the constructor
     */
    private class DecisionVariables {

        // -- map from partition to the corresponding decision variable
        private final Map<SoftwarePartition, GRBVar> vars;
        // -- the partition number variable which is part of the restricted growth string
        private final GRBVar partitionNumber;
        private DecisionVariables (Instance instance,
                                          ImmutableList<SoftwarePartition> partitions,
                                          GRBModel model) {
            this.vars = new HashMap<>();
            int numberOfCores=  partitions.size();

            try {

                partitionNumber = model.addVar(0.0, numberOfCores - 1, 0.0,
                        GRB.INTEGER, "a_" + instance.getInstanceName());

                for (SoftwarePartition p : partitions) {

                    GRBVar decisionInstancePartition = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("d_{$s}_{$s}", instance.getInstanceName(), p.toString()));
                    vars.put(p, decisionInstancePartition);
                }

            } catch (GRBException e) {
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                        "Could not build the decision variables for instance " + instance.getInstanceName()));

            }
        }

        /**
         * Builds and expression that can be used to constraint and instance to exactly one partition
         * @return A linear expression that represents the number of times an instance has been assigned
         * to a partition, to make sure each instance is assigned to exactly one partition, the returned expression
         * should be constrained to the value 1.0
         */
        public GRBLinExpr getUniquePartitionConstraint() {
            try {
                GRBLinExpr uniqueDecisionConstraint = new GRBLinExpr();
                GRBVar[] decisionVars = vars.values().toArray(new GRBVar[vars.values().size()]);
                double[] coeffs = new double [vars.values().size()];
                Arrays.fill(coeffs, 1.0);
                uniqueDecisionConstraint.addTerms(coeffs, decisionVars);
                return uniqueDecisionConstraint;

            } catch (GRBException e) {
                error("Could not create unique decision constraint expression");
            }
            return new GRBLinExpr();
        }

        public void getSymmetryBreakingConstraints(Instance instance) {

            try{

                // -- to break the symmetry we need to make sure that
                // a_i = p  <=>   d_i_p = true
                // I.e, if the partition index (partitionNumber) is equal to a partition index (i.e. core index) then
                // the decision variable assigning instance i to partition p is true
                for (SoftwarePartition p : vars.keySet()) {
                    GRBVar decisionVariable = vars.get(p);
                    int partitionAsIndex = p.toIndex();
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1.0, partitionNumber);
                    model.addGenConstrIndicator(decisionVariable, 1, expr, GRB.EQUAL, partitionAsIndex,
                            "symmetry_breaking_" + instance.getInstanceName() + "_" + p.toString());
                }

            } catch (GRBException e) {
                error("Could not build the symmetry breaking constraints");
            }
        }
        public GRBVar getPartitionNumber() {
            return this.partitionNumber;
        }
        public GRBVar getDecisionVariable(SoftwarePartition p) {
            return this.vars.get(p);
        }
    }


    /**
     * This function builds the symmetry breaking and core utilization constraints
     * @param instanceDecisionVariables
     * @param numberOfCores number of cores to be used
     */
    private void restrictedGrowthConstraint(Map<Instance, DecisionVariables> instanceDecisionVariables,
                                            int numberOfCores) {


        GRBVar firstActorVariable = instanceDecisionVariables.get(softwareActors.get(0)).getPartitionNumber();

        try {

            // -- the first actor can be assigned to either the first or the second core
            model.addConstr(firstActorVariable, GRB.LESS_EQUAL, 1.0,
                    "a_" + softwareActors.get(0).getInstanceName() + "_constraint");
            // -- the rest of the actors follow the restricted growth rule:
            // a_j <= max(a_1, a_2, ..., a_(j-1)) + 1
            // which means that between actor a_j can be either assigned to a new core
            // that no other actor is assigned to or be assigned to one the cores that
            // have already some actors assigned to them.
            for (int instIx = 1; instIx < softwareActors.size(); instIx ++) {

                Instance instance = softwareActors.get(instIx);
                // -- variable a_j
                GRBVar partitionVariable = instanceDecisionVariables.get(instance).getPartitionNumber();
                List<GRBVar> previousVariables = softwareActors.subList(0, instIx).stream()
                        .map(i -> instanceDecisionVariables.get(i).getPartitionNumber()).collect(Collectors.toList());
                GRBVar[] maxTerms = previousVariables.toArray(new GRBVar[previousVariables.size()]);
                GRBVar maxPrefix = model.addVar(0.0, numberOfCores - 1, 0.0,
                        GRB.INTEGER, "max(a_1,..,a_" + (instIx - 1) + ")");

                model.addGenConstrMax(maxPrefix, maxTerms, 0.0,
                        "a_" + instance.getInstanceName() + "_max_prefix_constraint");
                GRBLinExpr expr = new GRBLinExpr();
                expr.addConstant(1.0);
                expr.addTerm(1.0, maxPrefix);

                // -- finally the constraint a_j <= max(...) + 1.0
                model.addConstr(partitionVariable, GRB.LESS_EQUAL, expr,
                        "a_" + instance.getInstanceName() + "_restricted_growth");
            }

            // -- we also need to add a constraint on the number of cores used to make sure all cores are utilized
            GRBVar numCoresUsed =  model.addVar(0.0, numberOfCores - 1, 0.0, GRB.INTEGER,
                    "num_used_cores");
            List<GRBVar> allVars = softwareActors.stream()
                    .map(i -> instanceDecisionVariables.get(i).getPartitionNumber()).collect(Collectors.toList());
            GRBVar[] allVarsArray = allVars.toArray(new GRBVar[allVars.size()]);

            model.addGenConstrMax(numCoresUsed, allVarsArray, 0.0, "max_core_index_constraint");
            model.addConstr(numCoresUsed, GRB.EQUAL, numberOfCores - 1, "all_cores_used_constrant");


        } catch (GRBException e) {
            error("Could not build the symmetry breaking constraints");
        }


    }



    /**
     * Build a symmetric MILP model for performance and solve it
     * @param numberOfCores
     */
    public void solveModel(int numberOfCores) {

        // The full actor network
        Network network = task.getNetwork();

        String plink = uniquePlinkName(network, "system_plink");


        try {
            info("Starting pinned hardware partitioning on " + numberOfCores + " cores");
            GRBEnv env = new GRBEnv(true);

            Path logPath = context.getConfiguration().get(Compiler.targetPath).resolve("partitions.log");

            info("Logging into " + logPath.toAbsolutePath().toString());

            env.set("LogFile", logPath.toAbsolutePath().toString());

            env.start();

            this.model = new GRBModel(env);

            model.set(GRB.DoubleParam.TimeLimit, this.timeLimit);

            // -- create the parititions set
            ImmutableList<SoftwarePartition> partitions = makePartitionSet(numberOfCores);

            // -- declare the decision variables
            Map<Instance, DecisionVariables> instanceDecisionVariables = softwareActors.stream()
                    .collect(Collectors.toMap(Function.identity(),
                            instance -> new DecisionVariables(instance, partitions, model)));

            // -- we need to make sure every actor is mapped to exactly one partition
            for (Instance inst: instanceDecisionVariables.keySet()) {
                GRBLinExpr constraint = instanceDecisionVariables.get(inst).getUniquePartitionConstraint();
                model.addConstr(constraint, GRB.EQUAL, 1.0, inst.getInstanceName() + "_unique_partition");

            }
            // -- restricted growth and core utilization constraints
            restrictedGrowthConstraint(instanceDecisionVariables, numberOfCores);

            // -- symmetry breaking constraints
            for (Instance inst: instanceDecisionVariables.keySet()) {
                instanceDecisionVariables.get(inst).getSymmetryBreakingConstraints(inst);
            }


            // -- precompute the plink execution cost
            Double plinkKernelTime = getPlinkKernelCost();
            // -- precompute plink read and write cost
            Double plinkReadTime = getPlinkReadCost();
            Double plinkWriteTime = getPlinkWriteCost();

            // -- we assume the plink is synchronous so the total plink time (which is an actor) is the sum
            // of the times above
            Double plinkTime = plinkKernelTime + plinkReadTime + plinkWriteTime;

            // -- Formulate core execution time, note that plink is pinned to the first core

            // -- the upper bound for each partition is simply the sum of all actor execution times on software
            Double partitionTimeUpperBound = softwareActors.stream().map(
                    inst -> this.multicoreDB.getInstanceTicks(inst)
            ).reduce(Long::sum).orElseThrow(
                    () -> new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not" +
                            " compute partition execution time upper bound"))
            ).doubleValue() * this.multicoreClockPeriod;

            List<GRBVar> partitionExecutionTimeList = new ArrayList<>();
            for (SoftwarePartition partition: partitions) {


                GRBLinExpr partitionTimeExpression = getPartitionTimeExpression(partition, instanceDecisionVariables);
                if (partition.toIndex() == 0) {
                    // -- the plink is pinned to the first partition
                    partitionTimeExpression.addConstant(plinkTime);
                }
                GRBVar partitionExecTime = model.addVar(0.0, partitionTimeUpperBound, 0.0, GRB.CONTINUOUS,
                        "T_exec_" + partition.toString());
                // -- set the variable representing the partition time to the sum expression formulated above
                model.addConstr(partitionExecTime, GRB.EQUAL, partitionTimeExpression,
                        "T_exec_" + partition.toString() + "_constraint");

                partitionExecutionTimeList.add(partitionExecTime);
            }
            GRBVar[] partitionExecutionTimeArray = partitionExecutionTimeList.toArray(
                    new GRBVar[partitionExecutionTimeList.size()]);
            GRBVar executionTime = model.addVar(0.0, partitionTimeUpperBound, 0.0, GRB.CONTINUOUS,
                    "T_exec");
            model.addGenConstrMax(executionTime, partitionExecutionTimeArray, 0.0, "T_exec_constraints");
            // -- formulate the intra core communication, i.e., the local communication time on each partition,
            // note that this excludes the time spent communicating on first core with the plink

            // -- upper bound for local communication time
            // TODO: make this bound tighter!

            Double localCommunicationTimeUpperBound = network.getConnections().stream().map(
                    con -> this.multicoreDB.getCommunicationTicks(con,
                            CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod
            ).reduce(Double::sum).orElseThrow(() -> new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            "Could not compute local communication time upper bound")
            ));


            List<GRBVar> localCommunicationTimeList = new ArrayList<>();
            for (SoftwarePartition partition: partitions) {

                GRBLinExpr localCommunicationTimeExpression = getLocalCoreCommunicationExpression(partition,
                        instanceDecisionVariables);

                if (partition.toIndex() == 0){
                    GRBLinExpr plinkLocalCommunicationTimeExpression = getPlinkLocalCommunicationTime(
                            partition, instanceDecisionVariables);

                    localCommunicationTimeExpression.add(plinkLocalCommunicationTimeExpression);
                }

                GRBVar localCommunicationTimeInPartition = model.addVar(0.0, localCommunicationTimeUpperBound,
                        0.0, GRB.CONTINUOUS, "T_lc_" + partition.toString());

                model.addConstr(localCommunicationTimeInPartition, GRB.EQUAL, localCommunicationTimeExpression,
                        "T_lc_" + partition.toString() + "_constraint");

                localCommunicationTimeList.add(localCommunicationTimeInPartition);

            }

            GRBVar localCommunicationTime = model.addVar(0.0, localCommunicationTimeUpperBound, 0.0,
                    GRB.CONTINUOUS, "T_lc");
            GRBVar[] localCommunicationTimeInPartitionArray = localCommunicationTimeList.toArray(
                    new GRBVar[localCommunicationTimeList.size()]);
            model.addGenConstrMax(localCommunicationTime, localCommunicationTimeInPartitionArray,
                    0.0, "T_lc_constraint");



            // -- formulate the inter core communication time

            GRBLinExpr globalCommunicationTimeExpression =
                    getCoreToCoreCommunicationTime(partitions, instanceDecisionVariables);
            // -- add the plink global communication time
            GRBLinExpr plinkCoreToCoreCommunicationTimeExpression =
                    getPartitionToPlinkCommunicationExpression(partitions, instanceDecisionVariables);
            globalCommunicationTimeExpression.add(plinkCoreToCoreCommunicationTimeExpression);

            // -- compute and upper bound for the global communication time
            // TODO: Maybe make the bound tighter?
            Double globalCommunicationTimeUpperBound = network.getConnections().stream().map(
                    con -> this.multicoreDB.getCommunicationTicks(con,
                            CommonProfileDataBase.CommunicationTicks.Kind.Global) * this.multicoreClockPeriod
            ).reduce(Double::sum).orElseThrow(() -> new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            "Could not compute local communication time upper bound")
            ));
            GRBVar globalCommunicationTime = model.addVar(0.0, globalCommunicationTimeUpperBound,
                    0.0, GRB.CONTINUOUS, "T_cc");
            model.addConstr(globalCommunicationTime, GRB.EQUAL, globalCommunicationTimeExpression,
                    "T_cc_constraint");


            GRBLinExpr objectiveExpression = new GRBLinExpr();
            objectiveExpression.addTerm(1.0, executionTime);
            objectiveExpression.addTerm(1.0, localCommunicationTime);
            objectiveExpression.addTerm(1.0, globalCommunicationTime);

            GRBVar totalTime = model.addVar(0.0,
                    partitionTimeUpperBound + localCommunicationTimeUpperBound + globalCommunicationTimeUpperBound,
                    0.0, GRB.CONTINUOUS, "T");
            model.addConstr(totalTime, GRB.EQUAL, objectiveExpression, "total_time_constraint");

            model.setObjective(objectiveExpression, GRB.MINIMIZE);

            Path modelFile = logPath.resolve("model.lb");
            info("Writing the model into " + modelFile.toAbsolutePath().toString());

            model.write(modelFile.toString());

            model.presolve();
            model.optimize();



        } catch (GRBException e){
            fatalError("Could not solve or build the performance model: " + e.toString());
        }


    }

    /**
     * Formulate the local communication time for each partition, it does not include the
     * time spent communicating with the the plink on the first partition.
     * @param partition the partition for which the communication time is formulated
     * @param instanceDecisionVariables
     * @return A linear expression showing the time spent on each partition for local communications
     */
    private GRBLinExpr getLocalCoreCommunicationExpression(SoftwarePartition partition,
                                                           Map<Instance, DecisionVariables> instanceDecisionVariables) {

        GRBLinExpr expr = new GRBLinExpr();
        ImmutableList<Connection> softwareConnections = getSoftwareOnlyConnections();
        try {

            for (Connection connection: softwareConnections) {

                Instance sourceActor = findInstance(connection.getSource());
                Instance targetActor = findInstance(connection.getTarget());

                GRBVar sourceDecisionVariable = instanceDecisionVariables.get(sourceActor)
                        .getDecisionVariable(partition);
                GRBVar targetDecisionVariable  = instanceDecisionVariables.get(targetActor)
                        .getDecisionVariable(partition);

                GRBVar connectionOnTheSamePartition = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                        "d_" + partition.toString() + "_" + getConnectionName(connection));

                // -- the connection is local one if both source and target decision variables are 1, i.e., logical and
                // constraint
                GRBVar[] conjunctionConstraintArguments = new GRBVar[2];
                conjunctionConstraintArguments[0] = sourceDecisionVariable;
                conjunctionConstraintArguments[1] = targetDecisionVariable;

                model.addGenConstrAnd(connectionOnTheSamePartition, conjunctionConstraintArguments,
                        "connection_" + getConnectionName(connection) +
                                "_on_partition_" + partition.toString() + "_constraint");

                Double communicationTime = this.multicoreDB.getCommunicationTicks(
                        connection, CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod;
                expr.addTerm(communicationTime, connectionOnTheSamePartition);

            }

        } catch (GRBException e) {
            error("Could not formulate the local communication time");
        }

        return expr;
    }

    /**
     * The time spent on the first partition (i.e, the plinkPartition) communicating from other actors on the
     * same partition to the plink
     * @param plinkPartition the partition that contains the plink (i.e., the first partition)
     * @param instanceDecisionVariables a map from instance to decision variables
     * @return a linear expression formulating he time spent communicating to the plink from the the same core
     *          the plink is on
     */
    private GRBLinExpr getPlinkLocalCommunicationTime(SoftwarePartition plinkPartition,
                                                      Map<Instance, DecisionVariables> instanceDecisionVariables) {

        GRBLinExpr expr = new GRBLinExpr();

        // -- get the connection that cross hardware and software, these are the ones that can contribute
        // to the data copying to and from the plink.

        // -- connection from hardware to software
        ImmutableList<Connection> hardwareSoftwareConnections = getHardwareSoftwareConnections();
        // -- connection from software to hardware
        ImmutableList<Connection> softwareHardwareConnections = getSoftwareHardwareConnections();

        // -- local time when the source actor is on hardware and the target actor is on software
        for (Connection connection : hardwareSoftwareConnections) {

            // target actor is on software and source actor is on hardware
            Instance targetActor = findInstance(connection.getTarget());
            GRBVar targetActorDecisionVariable = instanceDecisionVariables.get(targetActor)
                    .getDecisionVariable(plinkPartition);
            Double communicationTime = this.multicoreDB.getCommunicationTicks(
                    connection, CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod;
            expr.addTerm(communicationTime, targetActorDecisionVariable);
        }

        // -- local time when the source actor is on software and the target actor is on hardware
        for (Connection connection: softwareHardwareConnections) {
            // source actor is on software
            Instance sourceActor = findInstance(connection.getSource());
            GRBVar sourceActorDecisionVariable = instanceDecisionVariables.get(sourceActor)
                    .getDecisionVariable(plinkPartition);
            Double communicationTime = this.multicoreDB.getCommunicationTicks(
                    connection, CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod;
            expr.addTerm(communicationTime, sourceActorDecisionVariable);
        }

        return expr;

    }

    private ImmutableList<Connection> getHardwareSoftwareConnections() {
        return task.getNetwork().getConnections().stream()
                .filter(connection -> {
                    Instance sourceActor = findInstance(connection.getSource());
                    Instance targetActor = findInstance(connection.getTarget());
                    return (hardwareActors.contains(sourceActor) && softwareActors.contains(targetActor));
                }).collect(ImmutableList.collector());
    }

    private ImmutableList<Connection> getSoftwareHardwareConnections() {

        return task.getNetwork().getConnections().stream()
                .filter(connection -> {
                    Instance sourceActor = findInstance(connection.getSource());
                    Instance targetActor = findInstance(connection.getTarget());
                    return (softwareActors.contains(sourceActor) && hardwareActors.contains(targetActor));
                }).collect(ImmutableList.collector());
    }

    private ImmutableList<Connection> getSoftwareOnlyConnections() {
        return task.getNetwork().getConnections().stream()
                .filter(connection -> {
                    Instance sourceActor = findInstance(connection.getSource());
                    Instance targetActor = findInstance(connection.getTarget());
                    return softwareActors.contains(sourceActor) && softwareActors.contains(targetActor);
                }).collect(ImmutableList.collector());
    }

    /**
     * Formulates the time spent in communicating to the plink, on the first core, from actors residing on other cores
     * @param partitions the list of all software partitions
     * @param instanceDecisionVariablesMap a map from instance to decision variables
     * @return a linear expression formulating time spent communicating to the plink from actors on cores not containing
     *         the plink
     */
    private GRBLinExpr getPartitionToPlinkCommunicationExpression(ImmutableList<SoftwarePartition> partitions,
            Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {

        // -- connection from hardware to software
        ImmutableList<Connection> hardwareSoftwareConnections = getHardwareSoftwareConnections();
        // -- connection from software to hardware
        ImmutableList<Connection> softwareHardwareConnections = getSoftwareHardwareConnections();

        // -- collect the none plink partitions
        ImmutableList<SoftwarePartition> nonePlinkPartitions = partitions.stream()
                .filter(p -> p.toIndex() != 0).collect(ImmutableList.collector());

        GRBLinExpr expr = new GRBLinExpr();

        // -- we want to formulate the amount of time spent on communicating
        // from none plink partitions to the plink partition (i.e., the first core)
        for (SoftwarePartition p: nonePlinkPartitions) {

            // outbound PLINK to other core communications
            for (Connection connection : hardwareSoftwareConnections) {

                Instance targetActor = findInstance(connection.getTarget());
                GRBVar targetDecisionVariable = instanceDecisionVariablesMap.get(targetActor)
                        .getDecisionVariable(p);
                Double communicationTime = this.multicoreDB.getCommunicationTicks(
                        connection, CommonProfileDataBase.CommunicationTicks.Kind.Global) *
                        this.multicoreClockPeriod;
                expr.addTerm(communicationTime, targetDecisionVariable);

            }
            // inbound PLINK to other cores communications
            for (Connection connection : softwareHardwareConnections) {
                Instance sourceActor = findInstance(connection.getSource());
                GRBVar sourceDecisionVariable = instanceDecisionVariablesMap.get(sourceActor)
                        .getDecisionVariable(p);
                Double communicationTime = this.multicoreDB.getCommunicationTicks(
                        connection, CommonProfileDataBase.CommunicationTicks.Kind.Global) *
                        this.multicoreClockPeriod;

                expr.addTerm(communicationTime, sourceDecisionVariable);
            }

        }

        return expr;
    }

    /**
     * Formulate the core to core communication cost
     * @param partitions the list of software partitions
     * @param instanceDecisionVariablesMap a map from instances to decision variables
     * @return a linear expression formulating the inter-core communication cost
     */
    private GRBLinExpr getCoreToCoreCommunicationTime(ImmutableList<SoftwarePartition> partitions,
                                                      Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {
        ImmutableList<Connection> softwareConnection = getSoftwareOnlyConnections();
        Set<SoftwarePartition> partitionSet = new HashSet<>(partitions);

        GRBLinExpr expr = new GRBLinExpr();
        for(Connection connection : softwareConnection) {

            for (SoftwarePartition psource: partitionSet) {

                Set<SoftwarePartition> exclusivePartitionSet = new HashSet<>(partitions);
                exclusivePartitionSet.remove(psource);

                for (SoftwarePartition ptarget: exclusivePartitionSet) {

                    Instance sourceActor = findInstance(connection.getSource());
                    Instance targetActor = findInstance(connection.getTarget());
                    GRBVar sourceDecisionVariable = instanceDecisionVariablesMap.get(sourceActor)
                            .getDecisionVariable(psource);
                    GRBVar targetDecisionVariable = instanceDecisionVariablesMap.get(targetActor)
                            .getDecisionVariable(ptarget);
                    try {
                        String variableName = "d_" + getConnectionName(connection) + "_" +
                                psource.toString() + "_" + ptarget.toString();
                        GRBVar conjunctionSourceTarget = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                variableName);
                        GRBVar[] conjunctionArguments = {
                                sourceDecisionVariable, targetDecisionVariable
                        };
                        model.addGenConstrAnd(conjunctionSourceTarget, conjunctionArguments,
                                variableName + "_and_constraint");

                        Double communicationTime = this.multicoreDB.getCommunicationTicks(
                                connection, CommonProfileDataBase.CommunicationTicks.Kind.Global
                        ) * this.multicoreClockPeriod;

                        expr.addTerm(communicationTime, conjunctionSourceTarget);

                    } catch (GRBException e) {
                        error("Could not formulate the " + psource.toString() + " to "  + ptarget.toString() +
                                " communication time for connection " + getConnectionName(connection));
                    }
                }

            }

        }

        return expr;

    }
    /**
     * Formulate the time spent communicating in each partition
     * @param partition the partition for which the time is formulated
     * @param instanceDecisionVariables a map from instance to decision variables
     * @return a linear expression formulating the local partition communication time
     */
    private GRBLinExpr getPartitionTimeExpression(SoftwarePartition partition,
                                                  Map<Instance, DecisionVariables> instanceDecisionVariables) {

        // The time spent in each partition is the sum of the times of each actor assigned to that partition
        GRBLinExpr expr = new GRBLinExpr();
        try {
            int numActors = softwareActors.size();
            GRBVar[] decisionVariables = softwareActors.stream().map(
                    instance -> instanceDecisionVariables.get(instance).getDecisionVariable(partition))
                    .collect(ImmutableList.collector()).toArray(new GRBVar[numActors]);
            double[] actorCost = new double[numActors];
            for (int ix = 0; ix < numActors; ix++)
                actorCost[ix] = this.multicoreDB.getInstanceTicks(softwareActors.get(ix))
                        .doubleValue() * this.multicoreClockPeriod;
            // if a decision variable is 1, then the cost of the corresponding actor is added to the partition cost
            expr.addTerms(actorCost, decisionVariables);
        } catch (GRBException e) {
            error("Could not formulate partition time");
        }
        return expr;
    }
    private Double getPlinkKernelCost() {
        return Collections.max(
                        hardwareActors.map(this.accelDB::getInstanceTicks)).doubleValue() * this.accelClockPeriod;
    }
    private Double getPlinkReadCost() {

        // -- we assume the plink is on the first core 0
        // A plink read cost has its source actor on hardware and target actor on software

        return task.getNetwork().getConnections().stream()
                .filter(connection -> {
                    // filter out connections that are not hardware to software connections
                    Instance sourceActor = findInstance(connection.getSource());
                    Instance targetActor = findInstance(connection.getTarget());
                    return hardwareActors.contains(sourceActor) && softwareActors.contains(targetActor);
                })
                .map(connection -> {
                    Long bufferSize = Long.valueOf(this.multicoreDB.getConnectionBytes(connection));
                    Long bytesExchanged = this.multicoreDB.getTokensExchanged(connection);
                    Double readTime = this.accelDB.getPCIeReadTime(bufferSize, bytesExchanged);
                    return readTime;
        }).reduce(Double::sum)
                .orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR,
                                        "Error computing plink read time")));

    }

    private Double getPlinkWriteCost() {
        // -- assuming the plink is on core 0, a fixed set of hardware and software actors the
        // plink write time can be precomputed

        return task.getNetwork().getConnections().stream()
                .filter(connection -> {
                    // -- filter out connections that are not software to hardware connections
                    Instance sourceActor = findInstance(connection.getSource());
                    Instance targetActor = findInstance(connection.getTarget());
                    return hardwareActors.contains(targetActor) && softwareActors.contains(sourceActor);

                })
                .map(connection -> {
                    Long bufferSize = Long.valueOf(this.multicoreDB.getConnectionBytes(connection));
                    Long bytesExchanged = this.multicoreDB.getTokensExchanged(connection);
                    Double writeTime = this.accelDB.getPCIeWriteTime(bufferSize, bytesExchanged);
                    return writeTime;
                }).reduce(Double::sum)
                .orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR, "Error computing plink write time")));

    }


}

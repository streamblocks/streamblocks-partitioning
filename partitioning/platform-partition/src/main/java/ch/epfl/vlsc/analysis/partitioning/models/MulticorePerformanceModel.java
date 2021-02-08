package ch.epfl.vlsc.analysis.partitioning.models;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import gurobi.*;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
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

public class MulticorePerformanceModel extends PerformanceModel {

  protected final MulticoreProfileDataBase multicoreDB;
  protected final Double multicoreClockPeriod;
  protected final Double timeLimit;
  private final ImmutableList<Instance> softwareActors;

  public MulticorePerformanceModel(
      CompilationTask task,
      Context context,
      MulticoreProfileDataBase multicoreDB,
      Double multicoreClockPeriod,
      Double timeLimit) {
    this.multicoreDB = multicoreDB;
    this.task = task;
    this.context = context;
    this.multicoreClockPeriod = multicoreClockPeriod;
    this.timeLimit = timeLimit;
    this.softwareActors = task.getNetwork().getInstances();
  }

  protected ImmutableList<SoftwarePartition> makeSoftwarePartitionSet(int numberOfCores) {

    ImmutableList.Builder<SoftwarePartition> builder = ImmutableList.builder();

    for (int i = 0; i < numberOfCores; i++) {
      builder.add(new SoftwarePartition(i));
    }

    return builder.build();
  }

  protected ImmutableList<Instance> getSoftwareActors() {
    return this.softwareActors;
  }

  protected void getSymmetryBreakingConstraints(Instance instance, DecisionVariables vars) {
    try {
      // -- to break the symmetry we need to make sure that
      // a_i = p  <=>   d_i_p = true
      // I.e, if the partition index (partitionNumber) is equal to a partition index (i.e. core
      // index) then
      // the decision variable assigning instance i to partition p is true
      for (TypedPartition p : vars.getPartitionSet()) {
        GRBVar decisionVariable = vars.getDecisionVariable(p);
        int partitionAsIndex = p.toIndex();
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1.0, vars.getPartitionNumber());
        model.addGenConstrIndicator(
            decisionVariable,
            1,
            expr,
            GRB.EQUAL,
            partitionAsIndex,
            "symmetry_breaking_" + instance.getInstanceName() + "_" + p.toString());
      }
    } catch (GRBException e) {
      fatalError("Could not build the symmtery breaking constraints");
    }
  }
  /**
   * This function builds the symmetry breaking and core utilization constraints
   *
   * @param instanceDecisionVariables
   * @param numberOfCores number of cores to be used
   */
  protected void restrictedGrowthConstraint(
      Map<Instance, DecisionVariables> instanceDecisionVariables, int numberOfCores) {

    GRBVar firstActorVariable =
        instanceDecisionVariables.get(getSoftwareActors().get(0)).getPartitionNumber();
    try {

      // -- the first actor can be assigned to either the first or the second core
      model.addConstr(
          firstActorVariable,
          GRB.EQUAL,
          0.0,
          "a_" + getSoftwareActors().get(0).getInstanceName() + "_constraint");
      // -- the rest of the actors follow the restricted growth rule:
      // a_j <= max(a_1, a_2, ..., a_(j-1)) + 1
      // which means that between actor a_j can be either assigned to a new core
      // that no other actor is assigned to or be assigned to one the cores that
      // have already some actors assigned to them.
      for (int instIx = 1; instIx < getSoftwareActors().size(); instIx++) {

        Instance instance = getSoftwareActors().get(instIx);
        // -- variable a_j
        GRBVar partitionVariable = instanceDecisionVariables.get(instance).getPartitionNumber();
        GRBVar[] maxTerms =
            getSoftwareActors().subList(0, instIx).stream()
                .map(i -> instanceDecisionVariables.get(i).getPartitionNumber())
                .toArray(GRBVar[]::new);
        GRBVar maxPrefix =
            model.addVar(
                0.0,
                numberOfCores - 1,
                0.0,
                GRB.INTEGER,
                "max_prefix_" + getSoftwareActors().get(instIx).getInstanceName());

        model.addGenConstrMax(
            maxPrefix, maxTerms, 0.0, "a_" + instance.getInstanceName() + "_max_prefix_constraint");
        GRBLinExpr expr = new GRBLinExpr();
        expr.addConstant(1.0);
        expr.addTerm(1.0, maxPrefix);

        // -- finally the constraint a_j <= max(...) + 1.0
        model.addConstr(
            partitionVariable,
            GRB.LESS_EQUAL,
            expr,
            "a_" + instance.getInstanceName() + "_restricted_growth");
      }

      // -- we also need to add a constraint on the number of cores used to make sure all cores are
      // utilized
      GRBVar numCoresUsed =
          model.addVar(0.0, numberOfCores - 1, 0.0, GRB.INTEGER, "num_used_cores");
      GRBVar[] allVarsArray =
          getSoftwareActors().stream()
              .map(i -> instanceDecisionVariables.get(i).getPartitionNumber())
              .toArray(GRBVar[]::new);

      model.addGenConstrMax(numCoresUsed, allVarsArray, 0.0, "max_core_index_constraint");
      model.addConstr(numCoresUsed, GRB.EQUAL, numberOfCores - 1, "all_cores_used_constrant");

    } catch (GRBException e) {
      error("Could not build the symmetry breaking constraints");
    }
  }
  /**
   * Formulate the core to core communication cost
   *
   * @param partitions the list of software partitions
   * @param instanceDecisionVariablesMap a map from instances to decision variables
   * @return a linear expression formulating the inter-core communication cost
   */
  protected GRBLinExpr getCoreToCoreCommunicationTime(
      ImmutableList<SoftwarePartition> partitions,
      Map<Instance, DecisionVariables> instanceDecisionVariablesMap,
      ImmutableList<Connection> softwareConnections) {
    //        ImmutableList<Connection> softwareConnection = getSoftwareOnlyConnections();
    Set<SoftwarePartition> partitionSet = new HashSet<>(partitions);

    GRBLinExpr expr = new GRBLinExpr();
    for (Connection connection : softwareConnections) {

      for (SoftwarePartition psource : partitionSet) {

        Set<SoftwarePartition> exclusivePartitionSet = new HashSet<>(partitions);
        exclusivePartitionSet.remove(psource);

        for (SoftwarePartition ptarget : exclusivePartitionSet) {

          Instance sourceActor = findInstance(connection.getSource());
          Instance targetActor = findInstance(connection.getTarget());
          GRBVar sourceDecisionVariable =
              instanceDecisionVariablesMap.get(sourceActor).getDecisionVariable(psource);
          GRBVar targetDecisionVariable =
              instanceDecisionVariablesMap.get(targetActor).getDecisionVariable(ptarget);
          try {
            String variableName =
                "d_"
                    + getConnectionName(connection)
                    + "_"
                    + psource.toString()
                    + "_"
                    + ptarget.toString();
            GRBVar conjunctionSourceTarget = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, variableName);
            GRBVar[] conjunctionArguments = {sourceDecisionVariable, targetDecisionVariable};
            model.addGenConstrAnd(
                conjunctionSourceTarget, conjunctionArguments, variableName + "_and_constraint");

            double communicationTime =
                this.multicoreDB.getCommunicationTicks(
                        connection, CommonProfileDataBase.CommunicationTicks.Kind.Global)
                    * this.multicoreClockPeriod;

            expr.addTerm(communicationTime, conjunctionSourceTarget);

          } catch (GRBException e) {
            error(
                "Could not formulate the "
                    + psource.toString()
                    + " to "
                    + ptarget.toString()
                    + " communication time for connection "
                    + getConnectionName(connection));
          }
        }
      }
    }

    return expr;
  }
  /**
   * Formulate the local communication time for each partition, it does not include the time spent
   * communicating with the the plink on the first partition.
   *
   * @param partition the partition for which the communication time is formulated
   * @param instanceDecisionVariables
   * @return A linear expression showing the time spent on each partition for local communications
   */
  protected GRBLinExpr getLocalCoreCommunicationExpression(
      SoftwarePartition partition,
      Map<Instance, DecisionVariables> instanceDecisionVariables,
      ImmutableList<Connection> softwareConnections) {

    GRBLinExpr expr = new GRBLinExpr();
    //        ImmutableList<Connection> softwareConnections = getSoftwareOnlyConnections();
    try {

      for (Connection connection : softwareConnections) {

        Instance sourceActor = findInstance(connection.getSource());
        Instance targetActor = findInstance(connection.getTarget());

        GRBVar sourceDecisionVariable =
            instanceDecisionVariables.get(sourceActor).getDecisionVariable(partition);
        GRBVar targetDecisionVariable =
            instanceDecisionVariables.get(targetActor).getDecisionVariable(partition);

        GRBVar connectionOnTheSamePartition =
            model.addVar(
                0.0,
                1.0,
                0.0,
                GRB.BINARY,
                "d_" + partition.toString() + "_" + getConnectionName(connection));

        // -- the connection is local one if both source and target decision variables are 1, i.e.,
        // logical and
        // constraint
        GRBVar[] conjunctionConstraintArguments = new GRBVar[2];
        conjunctionConstraintArguments[0] = sourceDecisionVariable;
        conjunctionConstraintArguments[1] = targetDecisionVariable;

        model.addGenConstrAnd(
            connectionOnTheSamePartition,
            conjunctionConstraintArguments,
            "connection_"
                + getConnectionName(connection)
                + "_on_partition_"
                + partition.toString()
                + "_constraint");

        double communicationTime =
            this.multicoreDB.getCommunicationTicks(
                    connection, CommonProfileDataBase.CommunicationTicks.Kind.Local)
                * this.multicoreClockPeriod;
        expr.addTerm(communicationTime, connectionOnTheSamePartition);
      }

    } catch (GRBException e) {
      error("Could not formulate the local communication time");
    }

    return expr;
  }
  /**
   * Formulate the time spent communicating in each partition
   *
   * @param partition the partition for which the time is formulated
   * @param instanceDecisionVariables a map from instance to decision variables
   * @return a linear expression formulating the local partition communication time
   */
  protected GRBLinExpr getPartitionTimeExpression(
      SoftwarePartition partition,
      Map<Instance, DecisionVariables> instanceDecisionVariables,
      ImmutableList<Instance> actors) {

    // The time spent in each partition is the sum of the times of each actor assigned to that
    // partition
    GRBLinExpr expr = new GRBLinExpr();
    try {
      int numActors = actors.size();
      GRBVar[] decisionVariables =
          actors.stream()
              .map(
                  instance ->
                      instanceDecisionVariables.get(instance).getDecisionVariable(partition))
              .collect(ImmutableList.collector())
              .toArray(new GRBVar[numActors]);
      double[] actorCost = new double[numActors];
      for (int ix = 0; ix < numActors; ix++)
        actorCost[ix] =
            this.multicoreDB.getInstanceTicks(actors.get(ix)).doubleValue()
                * this.multicoreClockPeriod;
      // if a decision variable is 1, then the cost of the corresponding actor is added to the
      // partition cost
      expr.addTerms(actorCost, decisionVariables);

    } catch (GRBException e) {
      error("Could not formulate partition time");
    }
    return expr;
  }

  private ImmutableList<Connection> getSoftwareOnlyConnections() {
    return task.getNetwork().getConnections().stream()
        .filter(
            connection -> {
              Instance sourceActor = findInstance(connection.getSource());
              Instance targetActor = findInstance(connection.getTarget());
              return getSoftwareActors().contains(sourceActor)
                  && getSoftwareActors().contains(targetActor);
            })
        .collect(ImmutableList.collector());
  }

  public int getMaxPartitions() {
    return getSoftwareActors().size();
  }

  /**
   * Build a symmetric MILP model for performance and solve it
   *
   * @param numberOfCores
   */
  public ImmutableList<PartitioningSolution<String>> solveModel(int numberOfCores) {

    // The full actor network
    Network network = task.getNetwork();

    // -- trivial none solution
    if (getMaxPartitions() < numberOfCores) return ImmutableList.empty();

    try {
      info("Starting pinned hardware partitioning on " + numberOfCores + " cores");
      GRBEnv env = new GRBEnv(true);

      Path logPath = context.getConfiguration().get(Compiler.targetPath);
      logPath.toFile().mkdirs();

      info("Logging into " + logPath.toAbsolutePath().toString());

      env.set("LogFile", logPath.resolve("gurobi.log").toAbsolutePath().toString());

      env.start();

      this.model = new GRBModel(env);

      model.set(GRB.DoubleParam.TimeLimit, this.timeLimit);

      // -- create the parititions set
      ImmutableList<SoftwarePartition> partitions = makeSoftwarePartitionSet(numberOfCores);
      ImmutableList<TypedPartition> basePartitions = partitions.map(p -> p);

      // -- declare the decision variables
      Map<Instance, DecisionVariables> instanceDecisionVariables =
          getSoftwareActors().stream()
              .collect(
                  Collectors.toMap(
                      Function.identity(),
                      instance -> new DecisionVariables(instance, basePartitions, model)));

      // -- we need to make sure every actor is mapped to exactly one partition
      for (Instance inst : instanceDecisionVariables.keySet()) {
        GRBLinExpr constraint = instanceDecisionVariables.get(inst).getUniquePartitionConstraint();
        model.addConstr(constraint, GRB.EQUAL, 1.0, inst.getInstanceName() + "_unique_partition");
      }
      // -- restricted growth and core utilization constraints
      restrictedGrowthConstraint(instanceDecisionVariables, numberOfCores);

      // -- symmetry breaking constraints
      for (Instance inst : instanceDecisionVariables.keySet()) {
        getSymmetryBreakingConstraints(inst, instanceDecisionVariables.get(inst));
      }

      // -- Formulate core execution time, note that plink is pinned to the first core

      // -- the upper bound for each partition is simply the sum of all actor execution times on
      // software
      Double partitionTimeUpperBound =
          getSoftwareActors().stream()
                  .map(this.multicoreDB::getInstanceTicks)
                  .reduce(Long::sum)
                  .orElseThrow(
                      () ->
                          new CompilationException(
                              new Diagnostic(
                                  Diagnostic.Kind.ERROR,
                                  "Could not" + " compute partition execution time upper bound")))
                  .doubleValue()
              * this.multicoreClockPeriod;

      List<GRBVar> partitionExecutionTimeList = new ArrayList<>();
      for (SoftwarePartition partition : partitions) {

        GRBLinExpr partitionTimeExpression =
            getPartitionTimeExpression(partition, instanceDecisionVariables, getSoftwareActors());

        GRBVar partitionExecTime =
            model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "T_exec_" + partition.toString());
        // -- set the variable representing the partition time to the sum expression formulated
        // above
        model.addConstr(
            partitionExecTime,
            GRB.EQUAL,
            partitionTimeExpression,
            "T_exec_" + partition.toString() + "_constraint");

        partitionExecutionTimeList.add(partitionExecTime);
      }
      GRBVar[] partitionExecutionTimeArray = partitionExecutionTimeList.toArray(new GRBVar[0]);
      GRBVar executionTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "T_exec");
      model.addGenConstrMax(executionTime, partitionExecutionTimeArray, 0.0, "T_exec_constraints");
      // -- formulate the intra core communication, i.e., the local communication time on each
      // partition,
      // note that this excludes the time spent communicating on first core with the plink

      // -- upper bound for local communication time
      // TODO: make this bound tighter!

      Double localCommunicationTimeUpperBound =
          network.getConnections().stream()
              .map(
                  con ->
                      this.multicoreDB.getCommunicationTicks(
                              con, CommonProfileDataBase.CommunicationTicks.Kind.Local)
                          * this.multicoreClockPeriod)
              .reduce(Double::sum)
              .orElseThrow(
                  () ->
                      new CompilationException(
                          new Diagnostic(
                              Diagnostic.Kind.ERROR,
                              "Could not compute local communication time upper bound")));

      List<GRBVar> localCommunicationTimeList = new ArrayList<>();
      for (SoftwarePartition partition : partitions) {

        GRBLinExpr localCommunicationTimeExpression =
            getLocalCoreCommunicationExpression(
                partition, instanceDecisionVariables, getSoftwareOnlyConnections());

        GRBVar localCommunicationTimeInPartition =
            model.addVar(
                0.0,
                localCommunicationTimeUpperBound,
                0.0,
                GRB.CONTINUOUS,
                "T_lc_" + partition.toString());

        model.addConstr(
            localCommunicationTimeInPartition,
            GRB.EQUAL,
            localCommunicationTimeExpression,
            "T_lc_" + partition.toString() + "_constraint");

        localCommunicationTimeList.add(localCommunicationTimeInPartition);
      }

      GRBVar localCommunicationTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "T_lc");
      GRBVar[] localCommunicationTimeInPartitionArray =
          localCommunicationTimeList.toArray(new GRBVar[localCommunicationTimeList.size()]);
      model.addGenConstrMax(
          localCommunicationTime, localCommunicationTimeInPartitionArray, 0.0, "T_lc_constraint");

      // -- formulate the inter core communication time

      GRBLinExpr globalCommunicationTimeExpression =
          getCoreToCoreCommunicationTime(
              partitions, instanceDecisionVariables, getSoftwareOnlyConnections());

      // -- compute and upper bound for the global communication time
      // TODO: Maybe make the bound tighter?
      Double globalCommunicationTimeUpperBound =
          network.getConnections().stream()
              .map(
                  con ->
                      this.multicoreDB.getCommunicationTicks(
                              con, CommonProfileDataBase.CommunicationTicks.Kind.Global)
                          * this.multicoreClockPeriod)
              .reduce(Double::sum)
              .orElseThrow(
                  () ->
                      new CompilationException(
                          new Diagnostic(
                              Diagnostic.Kind.ERROR,
                              "Could not compute local communication time upper bound")));
      GRBVar globalCommunicationTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "T_cc");
      model.addConstr(
          globalCommunicationTime, GRB.EQUAL, globalCommunicationTimeExpression, "T_cc_constraint");

      GRBLinExpr objectiveExpression = new GRBLinExpr();
      objectiveExpression.addTerm(1.0, executionTime);
      objectiveExpression.addTerm(1.0, localCommunicationTime);
      objectiveExpression.addTerm(1.0, globalCommunicationTime);

      GRBVar totalTime = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "T");
      model.addConstr(totalTime, GRB.EQUAL, objectiveExpression, "total_time_constraint");

      model.setObjective(objectiveExpression, GRB.MINIMIZE);

      Path modelFile = logPath.resolve("model.lp");
      info("Writing the model into " + modelFile.toAbsolutePath().toString());

      model.write(modelFile.toString());

      model.presolve();
      model.optimize();

      return collectSolutions(model, basePartitions, instanceDecisionVariables);

    } catch (GRBException e) {
      fatalError("Could not solve or build the performance model: " + e.toString());
    }

    return ImmutableList.empty();
  }

  private ImmutableList<PartitioningSolution<String>> collectSolutions(
      GRBModel model,
      ImmutableList<TypedPartition> partitionTypes,
      Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {

    ImmutableList.Builder<PartitioningSolution<String>> solutionBuilder = ImmutableList.builder();

    if (model == null) return ImmutableList.empty();

    try {
      int solutionCount = model.get(GRB.IntAttr.SolCount);

      for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
        model.set(GRB.IntParam.SolutionNumber, solutionIndex);
        solutionBuilder.add(createSolution(partitionTypes, instanceDecisionVariablesMap));
      }

    } catch (GRBException e) {
      fatalError("Could not obtains solutions: " + e.getMessage());
    }

    return solutionBuilder.build();
  }

  protected PartitioningSolution<String> createSolution(
      List<TypedPartition> partitionTypes,
      Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {

    ImmutableList.Builder<Partition<String>> partitionsBuilder = ImmutableList.builder();
    //        partitionTypes.sort(Comparator.comparingInt(SoftwarePartition::toIndex));

    for (TypedPartition p : partitionTypes) {

      ImmutableList<Instance> instancesInP =
          getSoftwareActors().stream()
              .filter(
                  inst -> {
                    GRBVar pNumber = instanceDecisionVariablesMap.get(inst).getPartitionNumber();
                    int partitionIndex = 0;
                    try {
                      Double pNumberDouble = pNumber.get(GRB.DoubleAttr.Xn);
                      Double floorValue = (double) pNumberDouble.intValue();
                      Double ceilValue = floorValue + 1;
                      if (pNumberDouble - floorValue < ceilValue - pNumberDouble) {
                        partitionIndex = floorValue.intValue();
                      } else {
                        partitionIndex = ceilValue.intValue();
                      }
                    } catch (GRBException e) {
                      fatalError(
                          "Could not get the partitionIndex value as Integer for instance "
                              + inst.getInstanceName()
                              + ": "
                              + e.getMessage());
                    }
                    return partitionIndex == p.toIndex();
                  })
              .collect(ImmutableList.collector());
      ImmutableList.Builder<String> instanceNames = ImmutableList.builder();

      instanceNames.addAll(instancesInP.map(Instance::getInstanceName));
      Partition<String> thisPartition = new Partition<String>(instanceNames.build(), p);

      partitionsBuilder.add(thisPartition);
    }
    return new PartitioningSolution<>(partitionsBuilder.build());
  }

  public void solutionsSummary(File dumpDir) {

    if (model == null) return;
    File dumpFile = new File(dumpDir + "/solutions.csv");
    try {
      PrintWriter solutionWriter = new PrintWriter(dumpFile);
      solutionWriter.println("T,T_exec,T_lc,T_cc");
      try {
        int solutionCount = model.get(GRB.IntAttr.SolCount);
        for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {

          model.set(GRB.IntParam.SolutionNumber, solutionIndex);
          System.out.println("Solution " + solutionIndex + ": ");
          printTimingBreakdown(solutionIndex, solutionWriter);
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

  private void printTimingBreakdown(int id, PrintWriter writer) throws GRBException {

    printVar("T");
    printVar("T_exec");
    printVar("T_lc");
    printVar("T_cc");
    writer.println(
        getVarByName("T")
            + ","
            + getVarByName("T_exec")
            + ","
            + getVarByName("T_lc")
            + ","
            + getVarByName("T_cc"));
  }

  private Double getVarByName(String name) throws GRBException {
    return model.getVarByName(name).get(GRB.DoubleAttr.Xn);
  }

  private void printVar(String name, Double value) {
    System.out.printf("%s = %6.6f s\n", name, value);
  }

  private void printVar(String name) throws GRBException {
    printVar(name, getVarByName(name));
  }
}

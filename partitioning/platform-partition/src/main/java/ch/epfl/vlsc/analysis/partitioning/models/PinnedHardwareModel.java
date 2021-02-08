package ch.epfl.vlsc.analysis.partitioning.models;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.DeviceProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import ch.epfl.vlsc.compiler.PartitionedCompilationTask;

import gurobi.*;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.ToolAttribute;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.*;
import java.util.List;

import java.util.function.Function;
import java.util.stream.Collectors;

public class PinnedHardwareModel extends MulticorePerformanceModel {

  private final MulticoreProfileDataBase multicoreDB;
  private final DeviceProfileDataBase accelDB;
  private final Double multicoreClockPeriod;
  private final Double accelClockPeriod;
  private final Double timeLimit;
  private final ImmutableList<Instance> softwareActors;
  private final ImmutableList<Instance> hardwareActors;

  public PinnedHardwareModel(
      CompilationTask task,
      Context context,
      MulticoreProfileDataBase multicoreDB,
      DeviceProfileDataBase accelDB,
      Double multicoreClockPeriod,
      Double accelClockPeriod,
      Double timeLimit) {
    super(task, context, multicoreDB, multicoreClockPeriod, timeLimit);
    this.multicoreDB = multicoreDB;
    this.accelDB = accelDB;
    this.multicoreClockPeriod = multicoreClockPeriod;
    this.accelClockPeriod = accelClockPeriod;
    this.timeLimit = timeLimit;
    this.context = context;
    this.task = task;

    this.softwareActors =
        task.getNetwork().getInstances().stream()
            .filter(i -> getInstancePartitionKind(i) == PartitionedCompilationTask.PartitionKind.SW)
            .collect(ImmutableList.collector());

    this.hardwareActors =
        task.getNetwork().getInstances().stream()
            .filter(i -> getInstancePartitionKind(i) == PartitionedCompilationTask.PartitionKind.HW)
            .collect(ImmutableList.collector());
  }

  @Override
  protected ImmutableList<Instance> getSoftwareActors() {
    return this.softwareActors;
  }

  private PartitionedCompilationTask.PartitionKind getInstancePartitionKind(Instance instance) {
    PartitionedCompilationTask.PartitionKind defaultPartition =
        PartitionedCompilationTask.PartitionKind.SW;
    ImmutableList<ToolAttribute> pattrs =
        ImmutableList.from(
            instance.getAttributes().stream()
                .filter(a -> a.getName().equals("partition"))
                .collect(Collectors.toList()));
    if (pattrs.size() == 0) {
      return defaultPartition;
    } else if (pattrs.size() == 1) {
      if (pattrs.get(0) instanceof ToolValueAttribute) {
        ToolValueAttribute attr = (ToolValueAttribute) pattrs.get(0);
        String p = ((ExprLiteral) attr.getValue()).asString().get();
        return (p.equals("hw")
            ? PartitionedCompilationTask.PartitionKind.HW
            : PartitionedCompilationTask.PartitionKind.SW);
      } else {
        error(
            "partition attribute of instance "
                + instance.getInstanceName()
                + "should be a value not a type");
        return PartitionedCompilationTask.PartitionKind.SW;
      }
    } else {
      info("using default sw partition for " + instance.getInstanceName());
      return defaultPartition;
    }
  }

  /**
   * Creates a unique name for an instance based on the given baseName
   *
   * @param network the network of instances
   * @param baseName the baseName for the instance, if there are already uses of this baseName,
   *     baneName_i is returned
   * @return a unique name
   */
  public static String uniquePlinkName(Network network, String baseName) {
    Set<String> names =
        network.getInstances().stream()
            .map(Instance::getInstanceName)
            .filter(n -> n.equals(baseName))
            .collect(Collectors.toSet());
    return baseName + "_" + names.size();
  }

  /**
   * Formulate the local communication time for each partition, it does not include the time spent
   * communicating with the the plink on the first partition.
   *
   * @param partition the partition for which the communication time is formulated
   * @param instanceDecisionVariables
   * @return A linear expression showing the time spent on each partition for local communications
   */
  @Override
  protected GRBLinExpr getLocalCoreCommunicationExpression(
      SoftwarePartition partition,
      Map<Instance, DecisionVariables> instanceDecisionVariables,
      ImmutableList<Connection> softwareConnections) {

    GRBLinExpr expr =
        super.getLocalCoreCommunicationExpression(
            partition, instanceDecisionVariables, softwareConnections);

    try {
      if (partition.toIndex() == 0) {
        GRBLinExpr plinkLocalCommunicationTimeExpression =
            getPlinkLocalCommunicationTime(partition, instanceDecisionVariables);

        expr.add(plinkLocalCommunicationTimeExpression);
      }

    } catch (GRBException e) {
      error("Could not formulate the local communication time");
    }

    return expr;
  }

  /**
   * The time spent on the first partition (i.e, the plinkPartition) communicating from other actors
   * on the same partition to the plink
   *
   * @param plinkPartition the partition that contains the plink (i.e., the first partition)
   * @param instanceDecisionVariables a map from instance to decision variables
   * @return a linear expression formulating he time spent communicating to the plink from the the
   *     same core the plink is on
   */
  private GRBLinExpr getPlinkLocalCommunicationTime(
      SoftwarePartition plinkPartition,
      Map<Instance, DecisionVariables> instanceDecisionVariables) {

    GRBLinExpr expr = new GRBLinExpr();

    // -- get the connection that cross hardware and software, these are the ones that can
    // contribute
    // to the data copying to and from the plink.

    // -- connection from hardware to software
    ImmutableList<Connection> hardwareSoftwareConnections = getHardwareSoftwareConnections();
    // -- connection from software to hardware
    ImmutableList<Connection> softwareHardwareConnections = getSoftwareHardwareConnections();

    // -- local time when the source actor is on hardware and the target actor is on software
    for (Connection connection : hardwareSoftwareConnections) {

      // target actor is on software and source actor is on hardware
      Instance targetActor = findInstance(connection.getTarget());
      GRBVar targetActorDecisionVariable =
          instanceDecisionVariables.get(targetActor).getDecisionVariable(plinkPartition);
      Double communicationTime =
          this.multicoreDB.getCommunicationTicks(
                  connection, CommonProfileDataBase.CommunicationTicks.Kind.Local)
              * this.multicoreClockPeriod;
      expr.addTerm(communicationTime, targetActorDecisionVariable);
    }

    // -- local time when the source actor is on software and the target actor is on hardware
    for (Connection connection : softwareHardwareConnections) {
      // source actor is on software
      Instance sourceActor = findInstance(connection.getSource());
      GRBVar sourceActorDecisionVariable =
          instanceDecisionVariables.get(sourceActor).getDecisionVariable(plinkPartition);
      double communicationTime =
          this.multicoreDB.getCommunicationTicks(
                  connection, CommonProfileDataBase.CommunicationTicks.Kind.Local)
              * this.multicoreClockPeriod;
      expr.addTerm(communicationTime, sourceActorDecisionVariable);
    }

    return expr;
  }

  private ImmutableList<Connection> getHardwareSoftwareConnections() {
    return task.getNetwork().getConnections().stream()
        .filter(
            connection -> {
              Instance sourceActor = findInstance(connection.getSource());
              Instance targetActor = findInstance(connection.getTarget());
              return (hardwareActors.contains(sourceActor) && softwareActors.contains(targetActor));
            })
        .collect(ImmutableList.collector());
  }

  private ImmutableList<Connection> getSoftwareHardwareConnections() {

    return task.getNetwork().getConnections().stream()
        .filter(
            connection -> {
              Instance sourceActor = findInstance(connection.getSource());
              Instance targetActor = findInstance(connection.getTarget());
              return (softwareActors.contains(sourceActor) && hardwareActors.contains(targetActor));
            })
        .collect(ImmutableList.collector());
  }

  private ImmutableList<Connection> getSoftwareOnlyConnections() {
    return task.getNetwork().getConnections().stream()
        .filter(
            connection -> {
              Instance sourceActor = findInstance(connection.getSource());
              Instance targetActor = findInstance(connection.getTarget());
              return softwareActors.contains(sourceActor) && softwareActors.contains(targetActor);
            })
        .collect(ImmutableList.collector());
  }

  /**
   * Formulates the time spent in communicating to the plink, on the first core, from actors
   * residing on other cores
   *
   * @param partitions the list of all software partitions
   * @param instanceDecisionVariablesMap a map from instance to decision variables
   * @return a linear expression formulating time spent communicating to the plink from actors on
   *     cores not containing the plink
   */
  private GRBLinExpr getPartitionToPlinkCommunicationExpression(
      ImmutableList<SoftwarePartition> partitions,
      Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {

    // -- connection from hardware to software
    ImmutableList<Connection> hardwareSoftwareConnections = getHardwareSoftwareConnections();
    // -- connection from software to hardware
    ImmutableList<Connection> softwareHardwareConnections = getSoftwareHardwareConnections();

    // -- collect the none plink partitions
    ImmutableList<SoftwarePartition> nonePlinkPartitions =
        partitions.stream().filter(p -> p.toIndex() != 0).collect(ImmutableList.collector());

    GRBLinExpr expr = new GRBLinExpr();

    // -- we want to formulate the amount of time spent on communicating
    // from none plink partitions to the plink partition (i.e., the first core)
    for (SoftwarePartition p : nonePlinkPartitions) {

      // outbound PLINK to other core communications
      for (Connection connection : hardwareSoftwareConnections) {

        Instance targetActor = findInstance(connection.getTarget());
        GRBVar targetDecisionVariable =
            instanceDecisionVariablesMap.get(targetActor).getDecisionVariable(p);
        Double communicationTime =
            this.multicoreDB.getCommunicationTicks(
                    connection, CommonProfileDataBase.CommunicationTicks.Kind.Global)
                * this.multicoreClockPeriod;
        expr.addTerm(communicationTime, targetDecisionVariable);
      }
      // inbound PLINK to other cores communications
      for (Connection connection : softwareHardwareConnections) {
        Instance sourceActor = findInstance(connection.getSource());
        GRBVar sourceDecisionVariable =
            instanceDecisionVariablesMap.get(sourceActor).getDecisionVariable(p);
        double communicationTime =
            this.multicoreDB.getCommunicationTicks(
                    connection, CommonProfileDataBase.CommunicationTicks.Kind.Global)
                * this.multicoreClockPeriod;

        expr.addTerm(communicationTime, sourceDecisionVariable);
      }
    }

    return expr;
  }

  /**
   * Formulate the core to core communication cost
   *
   * @param partitions the list of software partitions
   * @param instanceDecisionVariablesMap a map from instances to decision variables
   * @return a linear expression formulating the inter-core communication cost
   */
  @Override
  protected GRBLinExpr getCoreToCoreCommunicationTime(
      ImmutableList<SoftwarePartition> partitions,
      Map<Instance, DecisionVariables> instanceDecisionVariablesMap,
      ImmutableList<Connection> softwareConnections) {

    // -- add the plink global communication time
    GRBLinExpr plinkCoreToCoreCommunicationTimeExpression =
        getPartitionToPlinkCommunicationExpression(partitions, instanceDecisionVariablesMap);

    GRBLinExpr expr =
        super.getCoreToCoreCommunicationTime(
            partitions, instanceDecisionVariablesMap, softwareConnections);
    try {
      expr.add(plinkCoreToCoreCommunicationTimeExpression);
    } catch (GRBException e) {
      fatalError("Could not formulate the core to core communication with plink");
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
  @Override
  protected GRBLinExpr getPartitionTimeExpression(
      SoftwarePartition partition,
      Map<Instance, DecisionVariables> instanceDecisionVariables,
      ImmutableList<Instance> actors) {

    // The time spent in each partition is the sum of the times of each actor assigned to that
    // partition
    GRBLinExpr expr =
        super.getPartitionTimeExpression(partition, instanceDecisionVariables, actors);

    if (partition.toIndex() == 0) {
      Double plinkTime = getPlinkKernelCost() + getPlinkWriteCost() + getPlinkReadCost();

      //            plinkTime = 0.800;
      // -- the plink is pinned to the first partition
      expr.addConstant(plinkTime);
    }
    return expr;
  }

  public Double getPlinkKernelCost() {
    return Collections.max(hardwareActors.map(this.accelDB::getInstanceTicks)).doubleValue()
        * this.accelClockPeriod;
  }

  public Double getPlinkKernelCost2() {
    return Collections.max(hardwareActors.map(this::getHardwareActorCost));
  }

  private Double getProduction(Connection connection) {
    Long tokensExchanged = this.multicoreDB.getTokensExchanged(connection);
    Instance sourceInstance = findInstance(connection.getSource());
    double instanceExecTime = this.accelDB.getInstanceTicks(sourceInstance) * this.accelClockPeriod;
    return tokensExchanged.doubleValue() / instanceExecTime;
  }

  private Double getConsumption(Connection connection) {
    Long tokensExchanged = this.multicoreDB.getTokensExchanged(connection);
    Instance targetInstance = findInstance(connection.getTarget());
    double instanceExecTime = this.accelDB.getInstanceTicks(targetInstance) * this.accelClockPeriod;
    return tokensExchanged.doubleValue() / instanceExecTime;
  }

  private Double getHardwareActorCost(Instance instance) {

    Entity actor =
        task.getModule(GlobalNames.key).entityDecl(instance.getEntityName(), true).getEntity();
    Double instanceTime = this.accelDB.getInstanceTicks(instance) * this.accelClockPeriod;

    Function<PortDecl, Double> getActorOutputTime =
        output -> {
          Connection outputConnection =
              this.task.getNetwork().getConnections().stream()
                  .filter(
                      c ->
                          c.getSource().getInstance().isPresent()
                              && c.getSource()
                                  .getInstance()
                                  .get()
                                  .equals(instance.getInstanceName())
                              && c.getSource().getPort().equals(output.getName()))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new CompilationException(
                              new Diagnostic(
                                  Diagnostic.Kind.ERROR,
                                  "Could not find the connection for "
                                      + instance.getInstanceName()
                                      + "."
                                      + output.getName())));
          // is it a cross boundary connection?
          Instance consumerInstance = findInstance(outputConnection.getTarget());
          if (hardwareActors.contains(consumerInstance)) {

            Double prod = getProduction(outputConnection);
            Double cons = getConsumption(outputConnection);
            if (prod > cons) {
              int capacity = 1024;
              Long tokensExchanged = this.multicoreDB.getTokensExchanged(outputConnection);
              Long transientEnqueues = (capacity < tokensExchanged) ? capacity : tokensExchanged;
              Double transientTime = transientEnqueues / (prod - cons);

              Double steadyTime = 0.0;
              if (tokensExchanged.doubleValue() > prod * transientTime) {
                steadyTime = (tokensExchanged.doubleValue() - prod * transientTime) / cons;
              }
              return transientTime + steadyTime;
            }
          }

          return this.accelDB.getInstanceTicks(instance) * this.accelClockPeriod;
        };

    Function<PortDecl, Double> getActorInputTime =
        input -> {
          Connection inputConnection =
              this.task.getNetwork().getConnections().stream()
                  .filter(
                      c ->
                          c.getTarget().getInstance().isPresent()
                              && c.getTarget()
                                  .getInstance()
                                  .get()
                                  .equals(instance.getInstanceName())
                              && c.getTarget().getPort().equals(input.getName()))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new CompilationException(
                              new Diagnostic(
                                  Diagnostic.Kind.ERROR,
                                  "Could not find the connection for "
                                      + instance.getInstanceName()
                                      + "."
                                      + input.getName())));

          Instance producerInstance = findInstance(inputConnection.getSource());
          if (hardwareActors.contains(producerInstance)) {
            Double prod = getProduction(inputConnection);
            Double cons = getConsumption(inputConnection);

            if (cons > prod) {
              return (cons / prod) * instanceTime;
            }
          }
          return instanceTime;
        };

    ImmutableList<Double> outputProductionTimes = actor.getOutputPorts().map(getActorOutputTime);
    ImmutableList<Double> inputConsumptionTimes = actor.getInputPorts().map(getActorInputTime);
    return Collections.max(ImmutableList.concat(outputProductionTimes, inputConsumptionTimes));
  }

  public void reportHardwareConsumptionProductions() {

    ImmutableList<Connection> hardwareConnections =
        task.getNetwork().getConnections().stream()
            .filter(
                connection -> {
                  Instance sourceInstance = findInstance(connection.getSource());
                  Instance targetInstance = findInstance(connection.getTarget());
                  return hardwareActors.contains(sourceInstance)
                      && hardwareActors.contains(targetInstance);
                })
            .collect(ImmutableList.collector());
    System.out.printf("%29s\t\t%50s\t\t%29s\n", "production", "connection", "consumption");
    for (Connection connection : hardwareConnections) {
      Double prod = getProduction(connection);
      Double cons = getConsumption(connection);
      System.out.printf(
          "%09.9f(tokes/s)\t\t%50s\t\t%09.9f(tokens/s)\n",
          prod, getConnectionName(connection), cons);
    }
  }

  /**
   * Approximate the number of kernel calls based on the size of buffer and the number of tokens
   * that should be transferred via each PLink connection
   *
   * @return approximate number of kernel calls
   */
  public Double getNumberOfKernelCalls() {

    Function<Connection, Double> getNumberOfTransfers =
        connection -> {
          // -- number of bytes that should be transferred via connection
          Long bytesExchanged = this.multicoreDB.getBytesExchanged(connection);
          // -- size of the maximum payload
          Long bufferSize = Long.valueOf(this.multicoreDB.getConnectionBytes(connection));
          return bytesExchanged.doubleValue() / bufferSize.doubleValue();
        };
    Function<ImmutableList<Double>, Double> getMax =
        connections ->
            connections.stream()
                .max(Double::compare)
                .orElseThrow(
                    () ->
                        new CompilationException(
                            new Diagnostic(
                                Diagnostic.Kind.ERROR,
                                "Could not get the minimum number of reads for plink")));

    ImmutableList<Double> minNumberOfReads =
        getPlinkReadConnections().stream()
            .map(getNumberOfTransfers)
            .collect(ImmutableList.collector());
    Double minReads = getMax.apply(minNumberOfReads);

    ImmutableList<Double> minNumberOfWrites =
        getPlinkWriteConnections().stream()
            .map(getNumberOfTransfers)
            .collect(ImmutableList.collector());
    Double minWrites = getMax.apply(minNumberOfWrites);

    return Math.max(minReads, minWrites);
  }

  private ImmutableList<Connection> getPlinkReadConnections() {
    ImmutableList<Connection> readConnections =
        task.getNetwork().getConnections().stream()
            .filter(
                connection -> {
                  // filter out connections that are not hardware to software connections
                  Instance sourceActor = findInstance(connection.getSource());
                  Instance targetActor = findInstance(connection.getTarget());
                  return hardwareActors.contains(sourceActor)
                      && softwareActors.contains(targetActor);
                })
            .collect(ImmutableList.collector());
    return readConnections;
  }

  private ImmutableList<Connection> getPlinkWriteConnections() {
    ImmutableList<Connection> writeConnections =
        task.getNetwork().getConnections().stream()
            .filter(
                connection -> {
                  // -- filter out connections that are not software to hardware connections
                  Instance sourceActor = findInstance(connection.getSource());
                  Instance targetActor = findInstance(connection.getTarget());
                  return hardwareActors.contains(targetActor)
                      && softwareActors.contains(sourceActor);
                })
            .collect(ImmutableList.collector());
    return writeConnections;
  }

  public Double getPlinkReadCost() {

    // -- we assume the plink is on the first core 0
    // A plink read cost has its source actor on hardware and target actor on software
    Double numberOfTransfers = getNumberOfKernelCalls();

    ImmutableList<Connection> readConnections = getPlinkReadConnections();
    ImmutableList<Double> costs =
        readConnections.map(
            connection -> {
              Long bytesExchanged = this.multicoreDB.getBytesExchanged(connection);
              // -- virtual payload size in each transfer
              Double virtualPayloadSize = bytesExchanged.doubleValue() / numberOfTransfers;

              DeviceProfileDataBase.PCIeTicks ticks =
                  this.accelDB.getOverEstimation(virtualPayloadSize);
              Double outputStageTicks = ticks.getKernelTicks().doubleValue() / 2.0;
              Double readSizeTicks = ticks.getReadSizeTicks().doubleValue();
              Double readTicks = ticks.getReadTicks().doubleValue();

              Double averageTransferTime =
                  (outputStageTicks + readSizeTicks + readTicks)
                      * 1e-9
                      / ticks.getRepeats().doubleValue();

              Double readTime = averageTransferTime * numberOfTransfers;

              return readTime;
            });
    Double totalCost =
        costs.stream()
            .reduce(Double::sum)
            .orElseThrow(
                () ->
                    new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Error computing plink read time")));
    return totalCost;
  }

  public Double getPlinkWriteCost() {
    // -- assuming the plink is on core 0, a fixed set of hardware and software actors the
    // plink write time can be precomputed
    Double numberOfTransfers = getNumberOfKernelCalls();

    ImmutableList<Connection> writeConnections = getPlinkWriteConnections();
    ImmutableList<Double> costs =
        writeConnections.map(
            connection -> {
              Long bytesExchanged = this.multicoreDB.getBytesExchanged(connection);

              Double virtualPayloadSize = bytesExchanged.doubleValue() / numberOfTransfers;

              DeviceProfileDataBase.PCIeTicks ticks =
                  this.accelDB.getOverEstimation(virtualPayloadSize);

              Double inputStageTicks = ticks.getKernelTicks().doubleValue() / 2.0;
              Double readSizeTicks = ticks.getReadSizeTicks().doubleValue();
              Double writeTicks = ticks.getWriteTicks().doubleValue();

              Double averageTransferTime =
                  (inputStageTicks + readSizeTicks + writeTicks)
                      * 1e-9
                      / ticks.getRepeats().doubleValue();

              Double writeTime = averageTransferTime * numberOfTransfers;

              return writeTime;
            });

    Double costSum =
        costs.stream()
            .reduce(Double::sum)
            .orElseThrow(
                () ->
                    new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Error computing plink write time")));
    return costSum;
  }

  @Override
  protected PartitioningSolution<String> createSolution(
      List<TypedPartition> partitionTypes,
      Map<Instance, DecisionVariables> instanceDecisionVariablesMap) {

    ImmutableList.Builder<Partition<String>> partitionsBuilder = ImmutableList.builder();
    //        partitionTypes.sort(Comparator.comparingInt(SoftwarePartition::toIndex));

    for (TypedPartition p : partitionTypes) {

      ImmutableList<Instance> instancesInP =
          softwareActors.stream()
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
      if (p.toIndex() == 0) {
        instanceNames.add(uniquePlinkName(task.getNetwork(), "system_plink"));
      }
      instanceNames.addAll(instancesInP.map(Instance::getInstanceName));
      Partition<String> thisPartition = new Partition<String>(instanceNames.build(), p);

      partitionsBuilder.add(thisPartition);
    }
    return new PartitioningSolution<>(partitionsBuilder.build());
  }
}

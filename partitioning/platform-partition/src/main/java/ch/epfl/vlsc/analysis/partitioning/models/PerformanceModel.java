package ch.epfl.vlsc.analysis.partitioning.models;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import ch.epfl.vlsc.configuration.Configuration;

import ch.epfl.vlsc.configuration.ConfigurationManager;
import gurobi.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import se.lth.cs.tycho.compiler.CompilationTask;

import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

import java.util.*;
import java.util.function.Function;

public abstract class PerformanceModel {

  protected Context context;
  protected CompilationTask task;
  GRBModel model;

  public static class HardwarePartition implements TypedPartition {

    private final int index;

    public HardwarePartition(int i) {
      this.index = i;
    }

    @Override
    public int toIndex() {
      return this.index;
    }

    @Override
    public String toString() {
      return "accel";
    }
  }

  public static class SoftwarePartition implements TypedPartition {
    private final int index;

    public SoftwarePartition(int index) {
      this.index = index;
    }

    @Override
    public int toIndex() {
      return this.index;
    }

    @Override
    public String toString() {
      return "core_" + this.index;
    }
  }

  public abstract interface TypedPartition {
    public abstract int toIndex();

    public abstract String toString();
  }

  public static class Partition<T> {

    private final ImmutableList<T> instances;
    private final TypedPartition ptype;

    public Partition(ImmutableList<T> instances, TypedPartition p) {
      this.instances = instances;
      this.ptype = p;
    }

    public ImmutableList<T> getInstances() {
      return this.instances;
    }

    public TypedPartition getPartitionType() {
      return this.ptype;
    }
  }

  public static class PartitioningSolution<T> {
    private final ImmutableList<Partition<T>> partitions;

    public PartitioningSolution(ImmutableList<Partition<T>> partitions) {
      this.partitions = partitions;
    }

    public ImmutableList<Partition<T>> getPartitions() {
      return this.partitions;
    }
  }

  protected class DecisionVariables {

    // -- map from partition to the corresponding decision variable
    private final Map<TypedPartition, GRBVar> vars;
    // -- the partition number variable which is part of the restricted growth string
    private final GRBVar partitionNumber;

    protected DecisionVariables(
        Instance instance, List<TypedPartition> partitions, GRBModel model) {
      this.vars = new HashMap<>();
      int numberOfCores = partitions.size();

      try {

        partitionNumber =
            model.addVar(
                0.0, numberOfCores - 1, 0.0, GRB.INTEGER, "a_" + instance.getInstanceName());

        for (TypedPartition p : partitions) {

          GRBVar decisionInstancePartition =
              model.addVar(
                  0.0,
                  1.0,
                  0.0,
                  GRB.BINARY,
                  String.format("d_%s_%s", instance.getInstanceName(), p.toString()));
          vars.put(p, decisionInstancePartition);
        }

      } catch (GRBException e) {
        throw new CompilationException(
            new Diagnostic(
                Diagnostic.Kind.ERROR,
                "Could not build the decision variables for instance "
                    + instance.getInstanceName()));
      }
    }

    /**
     * Builds and expression that can be used to constraint and instance to exactly one partition
     *
     * @return A linear expression that represents the number of times an instance has been assigned
     *     to a partition, to make sure each instance is assigned to exactly one partition, the
     *     returned expression should be constrained to the value 1.0
     */
    public GRBLinExpr getUniquePartitionConstraint() {
      try {
        GRBLinExpr uniqueDecisionConstraint = new GRBLinExpr();
        GRBVar[] decisionVars = vars.values().toArray(new GRBVar[vars.values().size()]);
        double[] coeffs = new double[vars.values().size()];
        Arrays.fill(coeffs, 1.0);
        uniqueDecisionConstraint.addTerms(coeffs, decisionVars);
        return uniqueDecisionConstraint;

      } catch (GRBException e) {
        error("Could not create unique decision constraint expression");
      }
      return new GRBLinExpr();
    }

    public GRBVar getPartitionNumber() {
      return this.partitionNumber;
    }

    public GRBVar getDecisionVariable(TypedPartition p) {
      return this.vars.get(p);
    }

    public Set<TypedPartition> getPartitionSet() {
      return vars.keySet();
    }
  }

  protected Instance findInstance(Connection.End end) {

    String instanceName =
        end.getInstance()
            .orElseThrow(
                () ->
                    new CompilationException(
                        new Diagnostic(
                            Diagnostic.Kind.ERROR,
                            "Could not get instance from "
                                + "connection end None."
                                + end.getPort())));
    return task.getNetwork().getInstances().stream()
        .filter(instance -> instance.getInstanceName().equals(instanceName))
        .findFirst()
        .orElseThrow(
            () ->
                new CompilationException(
                    new Diagnostic(
                        Diagnostic.Kind.ERROR, "Could not find instance " + instanceName)));
  }

  protected String getConnectionName(Connection c) {

    return String.format(
        "{%s.%s->%s.%s}",
        c.getSource().getInstance().orElse(""),
        c.getSource().getPort(),
        c.getTarget().getInstance().orElse(""),
        c.getTarget().getPort());
  }

  protected void info(String message) {
    context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO, message));
  }

  protected void error(String message) {
    context.getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, message));
  }

  protected static void fatalError(String message) {
    throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, message));
  }

  public void dumpMulticoreConfig(
      String name,
      PartitioningSolution<String> partitionMap,
      MulticoreProfileDataBase multicoreDb) {

    File configFile = new File(name);

    try {
      // the config xml doc
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      Element configRoot = doc.createElement("configuration");
      doc.appendChild(configRoot);
      Element partitioningRoot = doc.createElement("partitioning");
      configRoot.appendChild(partitioningRoot);

      ImmutableList.Builder<String> availableInstancesBuilder = ImmutableList.builder();

      for (Partition<String> partition : partitionMap.getPartitions()) {
        ImmutableList<String> instances = partition.getInstances();
        if (!instances.isEmpty()) {
          Element partitionRoot = doc.createElement("partition");

          partitionRoot.setAttribute("id", String.valueOf(partition.getPartitionType().toIndex()));
          instances.forEach(
              instance -> {
                Element instanceRoot = doc.createElement("instance");
                instanceRoot.setAttribute("id", instance);
                partitionRoot.appendChild(instanceRoot);
              });
          partitioningRoot.appendChild(partitionRoot);
          availableInstancesBuilder.addAll(instances);
        }
      }

      Element connectionsRoot = doc.createElement("connections");

      ImmutableList<String> availableInstances = availableInstancesBuilder.build();

      Optional<String> virtualActor = Optional.of("system_plink_0");
      for (Connection connection : task.getNetwork().getConnections()) {

        String sourceInstance =
            connection
                .getSource()
                .getInstance()
                .orElseThrow(
                    () ->
                        new CompilationException(
                            new Diagnostic(
                                Diagnostic.Kind.ERROR,
                                "The network contains a "
                                    + "dangling connection "
                                    + connection.toString())));
        String targetInstance =
            connection
                .getTarget()
                .getInstance()
                .orElseThrow(
                    () ->
                        new CompilationException(
                            new Diagnostic(
                                Diagnostic.Kind.ERROR,
                                "The network contains a "
                                    + "dangling connection "
                                    + connection.toString())));

        String sourcePort = connection.getSource().getPort();
        String targetPort = connection.getTarget().getPort();
        Element fifoConnectionElem = doc.createElement("fifo-connection");
        String depth =
            String.valueOf(multicoreDb.getConnectionSettingsDataBase().get(connection).getDepth());
        if (availableInstances.contains(sourceInstance)
            && availableInstances.contains(targetInstance)) {
          fifoConnectionElem.setAttribute("source", sourceInstance);
          fifoConnectionElem.setAttribute("target", targetInstance);
          fifoConnectionElem.setAttribute("source-port", sourcePort);
          fifoConnectionElem.setAttribute("target-port", targetPort);
          fifoConnectionElem.setAttribute("size", depth);
          connectionsRoot.appendChild(fifoConnectionElem);
        } else if (availableInstances.contains(sourceInstance)
            && !availableInstances.contains(targetInstance)) {
          fifoConnectionElem.setAttribute("source", sourceInstance);
          fifoConnectionElem.setAttribute("target", virtualActor.get());
          fifoConnectionElem.setAttribute("source-port", sourcePort);
          fifoConnectionElem.setAttribute(
              "target-port",
              sourceInstance + "_" + sourcePort + "_" + targetPort + "_" + targetInstance);
          fifoConnectionElem.setAttribute("size", depth);
          connectionsRoot.appendChild(fifoConnectionElem);
        } else if (!availableInstances.contains(sourceInstance)
            && availableInstances.contains(targetInstance)) {
          fifoConnectionElem.setAttribute("source", virtualActor.get());
          fifoConnectionElem.setAttribute("target", targetInstance);
          fifoConnectionElem.setAttribute(
              "source-port",
              sourceInstance + "_" + sourcePort + "_" + targetPort + "_" + targetInstance);
          fifoConnectionElem.setAttribute("target-port", targetPort);
          fifoConnectionElem.setAttribute("size", depth);
          connectionsRoot.appendChild(fifoConnectionElem);
        }
      }

      configRoot.appendChild(connectionsRoot);

      StreamResult configStream = new StreamResult(configFile);
      DOMSource configDom = new DOMSource(doc);
      Transformer configTransformer = TransformerFactory.newInstance().newTransformer();
      configTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
      configTransformer.transform(configDom, configStream);

      context
          .getReporter()
          .report(
              new Diagnostic(
                  Diagnostic.Kind.INFO, "Config file saved to " + configFile.toString()));

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void dumpXcfConfig(
      String name,
      PartitioningSolution<Instance> partitionMap,
      Map<Connection, Integer> bufferDepthMap,
      CompilationTask task) {

    Configuration xcf = new Configuration();

    Configuration.Network xcfNetwork = new Configuration.Network();

    xcfNetwork.setId(task.getIdentifier().toString());
    xcf.setNetwork(xcfNetwork);

    // -- create the software partition
    Configuration.Partitioning partitioning = new Configuration.Partitioning();
    Configuration.Partitioning.Partition softwarePartition =
        new Configuration.Partitioning.Partition();

    softwarePartition.setId((short) 0);
    softwarePartition.setPe("x86_64");
    softwarePartition.setScheduling("ROUND_ROBIN");
    softwarePartition.setCodeGenerator("sw");
    softwarePartition.setHost(true);

    // -- create the hardware partition
    Configuration.Partitioning.Partition hardwarePartition =
        new Configuration.Partitioning.Partition();

    hardwarePartition.setId((short) 1);
    hardwarePartition.setPe("FPGA");
    hardwarePartition.setScheduling("FULL_PARALLEL");
    hardwarePartition.setCodeGenerator("hw");
    hardwarePartition.setHost(false);

    ImmutableList<Instance> softwareInstances =
        partitionMap.getPartitions().stream()
            .filter(p -> p.getPartitionType() instanceof SoftwarePartition)
            .flatMap(p -> p.getInstances().stream())
            .sorted(
                (i1, i2) ->
                    String.CASE_INSENSITIVE_ORDER.compare(
                        i1.getInstanceName(), i2.getInstanceName()))
            .collect(ImmutableList.collector());
    ImmutableList<Instance> hardwareInstances =
        partitionMap.getPartitions().stream()
            .filter(p -> p.getPartitionType() instanceof HardwarePartition)
            .flatMap(p -> p.getInstances().stream())
            .sorted(
                (i1, i2) ->
                    String.CASE_INSENSITIVE_ORDER.compare(
                        i1.getInstanceName(), i2.getInstanceName()))
            .collect(ImmutableList.collector());

    for (Instance instance : softwareInstances) {

      Configuration.Partitioning.Partition.Instance xcfInstance =
          new Configuration.Partitioning.Partition.Instance();
      xcfInstance.setId(instance.getInstanceName());
      softwarePartition.getInstance().add(xcfInstance);
    }

    for (Instance instance : hardwareInstances) {
      Configuration.Partitioning.Partition.Instance xcfInstance =
          new Configuration.Partitioning.Partition.Instance();
      xcfInstance.setId(instance.getInstanceName());
      hardwarePartition.getInstance().add(xcfInstance);
    }

    partitioning.getPartition().add(softwarePartition);
    partitioning.getPartition().add(hardwarePartition);

    xcf.setPartitioning(partitioning);

    // -- code generators

    Configuration.CodeGenerators xcfCodeGenerator = new Configuration.CodeGenerators();
    Configuration.CodeGenerators.CodeGenerator xcfSoftwareCodeGenerator =
        new Configuration.CodeGenerators.CodeGenerator();
    xcfSoftwareCodeGenerator.setId("sw");
    xcfSoftwareCodeGenerator.setPlatform("multicore");

    xcfCodeGenerator.getCodeGenerator().add(xcfSoftwareCodeGenerator);

    Configuration.CodeGenerators.CodeGenerator xcfHardwareCodeGenrator =
        new Configuration.CodeGenerators.CodeGenerator();

    xcfHardwareCodeGenrator.setId("hw");
    xcfHardwareCodeGenrator.setPlatform("vivado-hls");

    xcfCodeGenerator.getCodeGenerator().add(xcfHardwareCodeGenrator);

    xcf.setCodeGenerators(xcfCodeGenerator);

    // connections
    Configuration.Connections xcfConnections = new Configuration.Connections();

    for (Connection connection : task.getNetwork().getConnections()) {
      //            Configuration.Connections.FifoConnection fifo = new
      // Configuration.Connections.FifoConnection();
      //            fifo.setSize(4096);
      //            fifo.setSource(connection.getSource().getInstance().get());
      //            fifo.setTarget(connection.getTarget().getInstance().get());
      //            fifo.setSourcePort(connection.getSource().getPort());
      //            fifo.setTargetPort(connection.getTarget().getPort());
      //            xcfConnections.getFifoConnection().add(fifo);
    }

    xcf.setConnections(xcfConnections);

    try {
      File xcfFile = new File(name);
      ConfigurationManager.write(xcfFile, xcf);
    } catch (JAXBException e) {
      fatalError("Could not write the xcf file to " + name + ": " + e.getMessage());
    }
  }
}

package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileParser;
import ch.epfl.vlsc.analysis.partitioning.util.PartitionSettings;

import ch.epfl.vlsc.settings.PlatformSettings;
import gurobi.*;
import org.w3c.dom.*;


import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;


import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.Setting;
import se.lth.cs.tycho.compiler.Compiler;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartitioningAnalysisPhase implements Phase {


    MulticoreProfileDataBase multicoreDB;
    Double multicoreClockPeriod;


    boolean definedMulticoreProfilePath;
    boolean definedSystemCProfilePath;

    boolean definedOclProfilePath;
    boolean definedCoreCommProfilePath;

    public PartitioningAnalysisPhase() {
        this.multicoreDB = null;
        this.multicoreClockPeriod = 0.33;

        this.definedCoreCommProfilePath = false;
        this.definedOclProfilePath = false;
        this.definedSystemCProfilePath = false;
        this.definedMulticoreProfilePath = false;

    }
    @Override
    public String getDescription() {
        return "finds a partition based on profiling information";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                PartitionSettings.multiCoreProfilePath,
                PartitionSettings.systemCProfilePath,
                PartitionSettings.openCLProfilePath,
                PartitionSettings.multicoreCommunicationProfilePath,
                PartitionSettings.configPath,
                PartitionSettings.cpuCoreCount);
    }

    private void getRequiredSettings(CompilationTask task, Context context) throws CompilationException {

        Configuration configs = context.getConfiguration();
        Reporter reporter = context.getReporter();
        this.definedMulticoreProfilePath = configs.isDefined(PartitionSettings.multiCoreProfilePath);
        this.definedSystemCProfilePath = configs.isDefined(PartitionSettings.systemCProfilePath);
        this.definedOclProfilePath = configs.isDefined(PartitionSettings.openCLProfilePath);
        this.definedCoreCommProfilePath = configs.isDefined(PartitionSettings.multicoreCommunicationProfilePath);
//        this.definedCoreCommProfilePath = true;
        if (!this.definedMulticoreProfilePath) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format("Multicore profile path is not specified! use " +
                            "--set %s=<PATH TO XML FILE> to set the profiling path",
                                    PartitionSettings.multiCoreProfilePath)));

        }
        if (!this.definedSystemCProfilePath) {

            reporter.report(
                    new Diagnostic(Diagnostic.Kind.WARNING ,
                            String.format("%s not specified, " +
                                    "the partitioning will not contain an FPGA partition, " +
                                    "set the value using --set %1$s=<PATH TO XML FILE>",
                                    PartitionSettings.systemCProfilePath.getKey())));

        }

        if (!this.definedOclProfilePath) {

            reporter.report(
                    new Diagnostic(Diagnostic.Kind.WARNING,
                            String.format("%s is not specified, the partitioning may be inaccurate. Use" +
                                    "--set %1$s=<PATH TO XML FILE>", PartitionSettings.openCLProfilePath.getKey())));


        }

        if (!this.definedCoreCommProfilePath) {

            reporter.report(
                    new Diagnostic(Diagnostic.Kind.WARNING,
                            String.format("%s is not specified, the partitioning may be inaccurate. Use" +
                                    "--set %1$s=<PATH TO XML FILE>",
                                    PartitionSettings.multicoreCommunicationProfilePath.getKey())));

        }

    }
    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        Network network = task.getNetwork();

        getRequiredSettings(task, context);
        MulticoreProfileParser multicoreParser = new MulticoreProfileParser(task, context, this.multicoreClockPeriod);
        multicoreParser.parseExecutionProfile(context.getConfiguration().get(PartitionSettings.multiCoreProfilePath));
        if (this.definedCoreCommProfilePath) {
            multicoreParser.parseBandwidthProfile(context.getConfiguration().get(PartitionSettings.multicoreCommunicationProfilePath));
        }
        this.multicoreDB = multicoreParser.getDataBase();
        Map<Integer, List<Instance>> partitions = findPartitions(task, context);
        createConfig(partitions, context);

        return task;
    }

    private void createConfig(Map<Integer, List<Instance>> partitions, Context context) {

        Path configPath =
            context.getConfiguration().isDefined(PartitionSettings.configPath) ?
                context.getConfiguration().get(PartitionSettings.configPath) :
                    context.getConfiguration().get(Compiler.targetPath).resolve("bin/config.xml");

        try {
            // the config xml doc
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element configRoot = doc.createElement("Configuration");
            doc.appendChild(configRoot);
            Element partitioningRoot = doc.createElement("Partitioning");
            configRoot.appendChild(partitioningRoot);

            partitions.forEach( (partition, instances) -> {
                Element partitionRoot = doc.createElement("Partition");
                partitionRoot.setAttribute("id", partition.toString());
                instances.forEach( instance -> {
                    Element instanceRoot = doc.createElement("Instance");
                    instanceRoot.setAttribute("actor-id", instance.getInstanceName() + "/0");
                    partitionRoot.appendChild(instanceRoot);
                });
                partitioningRoot.appendChild(partitionRoot);
            });

            StreamResult configStream = new StreamResult(new File(configPath.toUri()));
            DOMSource configDom = new DOMSource(doc);
            Transformer configTransformer = TransformerFactory.newInstance().newTransformer();
            configTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
            configTransformer.transform(configDom, configStream);

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Config file saved to " + configPath.toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Map<Integer, List<Instance>> findPartitions(CompilationTask task, Context context) {

        Network network = task.getNetwork();
        Map<Integer, List<Instance>> partitions = new HashMap<>();
        try {

            GRBEnv env = new GRBEnv(true);
            Path logPath = context.getConfiguration().get(Compiler.targetPath);
            Path logfile = logPath.resolve("partitions.log");

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Logging into " + logfile.toString()));

            env.set("LogFile", logfile.toString());


            env.start();

            GRBModel model = new GRBModel(env);

            // Set the objective to minimize total ticks
            GRBLinExpr objectiveExpr = new GRBLinExpr();

            int numPartitions =
                    context.getConfiguration().isDefined(PartitionSettings.cpuCoreCount) ?
                            context.getConfiguration().get(PartitionSettings.cpuCoreCount) :
                            PartitionSettings.cpuCoreCount.defaultValue(context.getConfiguration());
            Map<Instance, List<GRBVar>> partitionVars = new HashMap<>();

            // Partition variables
            for (Instance instance : network.getInstances()) {
                GRBLinExpr constraintExpr = new GRBLinExpr();
                List<GRBVar> vars = new ArrayList<>();
                for (int part = 0; part < numPartitions; part++) {
                    GRBVar partitionSelector =
                            model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                    String.format("p_%d_%s", part, instance.getInstanceName()));
                    vars.add(partitionSelector);
                    constraintExpr.addTerm(1.0, partitionSelector);
                }
                partitionVars.put(instance, vars);
                // unique partition constraint
                model.addConstr(constraintExpr, GRB.EQUAL,
                        1.0, String.format("unique partition %s", instance.getInstanceName()));
            }

            GRBVar[] execTicks = new GRBVar[numPartitions];
            // Partition execution tick
            for (int part = 0; part < numPartitions; part++) {
                GRBVar partitionTicks =
                        model.addVar(0.0, ticksUpperBound(), 0.0, GRB.CONTINUOUS, "ticks_" + part);
                execTicks[part] = partitionTicks;
                GRBLinExpr ticksExpr = new GRBLinExpr();
                for (Instance instance : network.getInstances()) {
                    GRBVar partitionSelector = partitionVars.get(instance).get(part);
                    Long instanceTicks = this.multicoreDB.getInstanceTicks(instance);

                    ticksExpr.addTerm(instanceTicks, partitionSelector);
                }
                // partition ticks is the sum of ticks
                model.addConstr(partitionTicks, GRB.EQUAL, ticksExpr,
                        String.format("ticks constraint %d", part));
            }
            GRBVar totalExecTicks = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "total_execution_ticks");

            // Total ticks is the max of parallel ticks
            model.addGenConstrMax(totalExecTicks, execTicks, 0.0, "parallel tasks time constraint");

            objectiveExpr.addTerm(1.0, totalExecTicks);



            Map<Connection, GRBVar> commTicks = new HashMap<>();

            if (definedCoreCommProfilePath) {
                // communication cost
                for (Connection connection : network.getConnections()) {
                    Connection.End sourceEnd = connection.getSource();

                    Connection.End targetEnd = connection.getTarget();

                    Instance sourceInstance = findSourceInstance(sourceEnd, network);
                    Instance targetInstance = findTargetInstance(targetEnd, network);

                    List<GRBVar> sourceVars = partitionVars.get(sourceInstance);
                    List<GRBVar> targetVars = partitionVars.get(targetInstance);
                    GRBQuadExpr sumOfProducts = new GRBQuadExpr();
                    for (int sourcePart = 0; sourcePart < numPartitions; sourcePart++) {

                        for (int targetPart = 0; targetPart < numPartitions; targetPart++) {
                            GRBVar sourcePartVar = sourceVars.get(sourcePart);
                            GRBVar targetPartVar = targetVars.get(targetPart);
                            Double intraTicks =
                                    this.multicoreDB.getCommunicationTicks(connection,
                                            MulticoreProfileDataBase.CommunicationTicks.Kind.LocalCore);

                            Double interTicks =
                                    this.multicoreDB.getCommunicationTicks(connection,
                                            MulticoreProfileDataBase.CommunicationTicks.Kind.Core2Core);

                            Double commTime = Double.valueOf(0);
                            if (sourcePart == targetPart)
                                commTime = intraTicks;
                            else
                                commTime = interTicks;
                            sumOfProducts.addTerm(commTime, sourcePartVar, targetPartVar);

                        }
                    }
                    String connectionName = String.format("%s.%s->%s.%s",
                            sourceEnd.getInstance().get(), sourceEnd.getPort(),
                            targetEnd.getInstance().get(), targetEnd.getPort());
                    GRBVar connectionTicks = model.addVar(
                            0.0,
                            GRB.INFINITY,
                            0.0,
                            GRB.CONTINUOUS,
                            connectionName + "_ticks");
                    commTicks.put(connection, connectionTicks);
                    model.addQConstr(connectionTicks, GRB.EQUAL, sumOfProducts, "constraint " + connectionName);
                }

                GRBVar totalCommTicks = model.addVar(
                        0.0,
                        GRB.INFINITY,
                        0.0,
                        GRB.CONTINUOUS,
                        "total communication ticks");
                GRBLinExpr sumCommTicks = new GRBLinExpr();
                commTicks.values().forEach(v -> sumCommTicks.addTerm(1.0, v));
                model.addConstr(totalCommTicks, GRB.EQUAL, sumCommTicks, "sum of communication ticks");
                objectiveExpr.addTerm(1.0, totalCommTicks);
            }


            model.setObjective(objectiveExpr, GRB.MINIMIZE);

            Path modelFile = logPath.resolve("model.lp");
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Writing model into " + modelFile.toString()));
            model.write(modelFile.toString());


            model.presolve();
            // Find a solution
            model.optimize();

            context.getReporter().report(
                    new Diagnostic(
                            Diagnostic.Kind.INFO, String.format("Partitions 0 to %d: ", numPartitions - 1)));

            for (Instance instance: network.getInstances()) {
                int maxIndex = 0;
                double maxVal = 0;
                for (GRBVar part: partitionVars.get(instance)) {
                    if (part.get(GRB.DoubleAttr.X) > maxVal) {
                        maxVal = part.get(GRB.DoubleAttr.X);
                        maxIndex = partitionVars.get(instance).indexOf(part);
                    }
                }
                if (partitions.containsKey(maxIndex)) {
                    partitions.get(maxIndex).add(instance);
                } else {
                    List<Instance> ls = new ArrayList<Instance>();
                    ls.add(instance);
                    partitions.put(maxIndex, ls);
                }
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.INFO,
                                String.format("Instance %s -> partition %d", instance.getInstanceName(), maxIndex)));
            }

            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
        return partitions;
    }
    private String stripAffinity(String orig) {
        return orig.substring(0, orig.indexOf("/"));
    }


    /**TODO: handle potential errors*/
    private Instance findSourceInstance(Connection.End source, Network network) {

        return network.getInstances().stream()
                .filter(
                    instance ->
                        instance.getInstanceName().equals(source.getInstance().get())
                    ).findFirst().get();

    }
    /**TODO: handle potential errors*/
    private Instance findTargetInstance(Connection.End target, Network network) {
        return network.getInstances().stream()
                .filter(
                    instance ->
                        instance.getInstanceName().equals(target.getInstance().get())

                        ).findFirst().get();
    }


    private Long ticksUpperBound() {
        return this.multicoreDB.getExecutionProfileDataBase().values().stream().reduce((a, b) -> a + b).get();
    }



}

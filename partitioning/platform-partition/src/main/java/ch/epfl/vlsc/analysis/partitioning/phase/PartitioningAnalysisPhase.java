package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.DeviceProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileParser;
import ch.epfl.vlsc.analysis.partitioning.util.PartitionSettings;

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
import java.util.*;
import java.util.stream.Collectors;

public class PartitioningAnalysisPhase implements Phase {


    MulticoreProfileDataBase multicoreDB;
    DeviceProfileDataBase accelDB;

    Double multicoreClockPeriod; // in NS
    Double accelClockPeriod; // in NS


    boolean definedMulticoreProfilePath;
    boolean definedSystemCProfilePath;

    boolean definedOclProfilePath;
    boolean definedCoreCommProfilePath;

    public PartitioningAnalysisPhase() {
        this.multicoreDB = null;
        this.multicoreClockPeriod = 0.33; // about 3 GHz
        this.accelClockPeriod = 3.3; // about 300 MHz
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
                    instanceRoot.setAttribute("actor-id", instance.getInstanceName());
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
    private class InstancePartitionVariables {
        private Map<String, GRBVar> b_i_p;
        public InstancePartitionVariables () {
            this.b_i_p = new HashMap<>();
        }
        public void put(String p, GRBVar var) {
            this.b_i_p.put(p, var);
        }
        public GRBVar get(String p) {
            return b_i_p.get(p);
        }
        public Collection<GRBVar> getAll() {
            return b_i_p.values();
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

            // create the objective expression
            GRBLinExpr objectiveExpr = new GRBLinExpr();

            /**
             * To simplify the formulas (without loss of generality) we assume that the
             */
            // --Define the partition variable \forall i \in I, \forall p \in

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
                            Double intraTicks = Double.valueOf(0);
                            Double interTicks = Double.valueOf(0);
                            try {
                                intraTicks =
                                        this.multicoreDB.getCommunicationTicks(connection,
                                                CommonProfileDataBase.CommunicationTicks.Kind.Local);
                                interTicks =
                                        this.multicoreDB.getCommunicationTicks(connection,
                                                CommonProfileDataBase.CommunicationTicks.Kind.External);
                            } catch (CompilationException e) {
                                intraTicks = this.multicoreDB.getCommunicationTicks(connection,
                                        this.multicoreDB.getMinimumProfiledBufferSize(),
                                        CommonProfileDataBase.CommunicationTicks.Kind.Local);
                                interTicks = this.multicoreDB.getCommunicationTicks(connection,
                                        this.multicoreDB.getMinimumProfiledBufferSize(),
                                        CommonProfileDataBase.CommunicationTicks.Kind.External);
                            }

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

    private Map<String, List<Instance>> findHeterogeneousPartitions(CompilationTask task, Context context) {
        Network network = task.getNetwork();
        Map<String, List<Instance>> partitions = new HashMap<>();
        try {

            GRBEnv env = new GRBEnv(true);
            Path logPath = context.getConfiguration().get(Compiler.targetPath);
            Path logfile = logPath.resolve("partitions.log");

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Logging into " + logfile.toString()));

            env.set("LogFile", logfile.toString());


            env.start();

            GRBModel model = new GRBModel(env);

            // create the objective expression
            GRBLinExpr objectiveExpr = new GRBLinExpr();


            int numberOfCores = context.getConfiguration().get(PartitionSettings.cpuCoreCount);
            if (numberOfCores < 1) {
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, String.format("Invalid number of cores %d, " +
                                "number of cores should be larger that 0.", numberOfCores)));
            }

            // Set of all partitions
            Set<String> P = new HashSet<>();
            // (Set) of all instances
            ImmutableList<Instance> I = network.getInstances();
            // (Set) of all connections
            ImmutableList<Connection> C = network.getConnections();


            /**
             * The set of all partitions is P = {c_m : 1 \leq m \leq numberOfCores} \cup {accel}
             */

            String accelPartition = "accel";
            String plinkPartition = "core_0";

            P.add(plinkPartition);
            for (int p = 1; p < numberOfCores; p++)
                P.add("core_" + p );
            if (this.definedSystemCProfilePath)
                P.add(accelPartition);

            // A map from instances to their associated variables
            Map<Instance, InstancePartitionVariables> instanceVariables = new HashMap<>();

            /**
             * instantiate the GRBVariables b^i_p for each instance and create the constraint:
             *  \forall i \in I: \sum_{p \in P} b^i_p = 1
             */
            for (Instance i: I) {

                InstancePartitionVariables b_i = new InstancePartitionVariables();
                GRBLinExpr constraintExpr = new GRBLinExpr();
                for (String p : P) {

                    GRBVar b_i_p = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, String.format("b^{%s}_{%s}",
                            i.getInstanceName(), p));
                    b_i.put(p, b_i_p);
                    constraintExpr.addTerm(1.0, b_i_p);
                }

                instanceVariables.put(i, b_i);
                model.addConstr(constraintExpr, GRB.EQUAL, 1.0,
                        String.format("%s:unique_partition", i.getInstanceName()));
            }

            /**
             * Define the PLINK variables
             * t^{plink}_{kernel} = max(\{b^i_\{accel\} \times t^i_{exec}(accel): i \in I\})
             * t^{plink}_{write} = \sum_{(l, k) \in C} (1 - b^l_{accel}) b^k_{accel} n_{(l,k)} bw^{(l, k)}(ca)
             * t^{plink}_{read} = \sum_{(l, k) \in C} b^l_{accel}(1 - b^k_{accel}) n_{(l,k)} bw^{(l, k)}(ca)
             *
             */
            // Total time spent by PLINK
            Optional<GRBVar> T_plink = Optional.empty();
            Double upperPlinkTime = Double.valueOf(0);
            if (this.definedSystemCProfilePath) {

                // First off, the kernel time
                // terms list for t^{plink}_{kernel} = max(\{b^i_\{accel\} \times t^i_{exec}(accel): i \in I\})
                List<GRBVar> termsList = new ArrayList<>();
                for (Instance i : I) {

                    Double t_i_exec_accel = this.accelDB.getInstanceTicks(i).doubleValue() * this.accelClockPeriod;

                    GRBVar b_i_accel_t_i_exec_product =  model.addVar(0.0,
                            t_i_exec_accel, 0.0, GRB.CONTINUOUS,
                            String.format("b^%s_{accel}xt^%1$s_{exec}(accel)", i.getInstanceName()));
                    GRBLinExpr expr = new GRBLinExpr();

                    // The time of each instance on the FPGA =  ticks * clockPeriod

                    GRBVar b_i_accel = instanceVariables.get(i).get(accelPartition);
                    expr.addTerm(t_i_exec_accel, b_i_accel);
                    model.addConstr(expr, GRB.EQUAL, b_i_accel_t_i_exec_product,
                            String.format("b_%s_{accel}_t_%1$s_{exec}=b^%1$s_{accel}xt^%1$s_{exec}(accel)",
                                    i.getInstanceName()));
                    termsList.add(b_i_accel_t_i_exec_product);
                }
                // The upper bound on the accel partion is the maximum of all instance times, while the upper
                // bound on a multicore partition is the sum of all instance times
                Double upperAccelTime = Collections.max(
                        this.accelDB.getExecutionProfileDataBase().values())
                        .doubleValue() * this.accelClockPeriod;
                GRBVar t_plink_kernel = model.addVar(0.0, upperAccelTime, 0.0, GRB.CONTINUOUS,
                        "t_plink_kernel");

                GRBVar[] maxTermsArray = termsList.toArray(new GRBVar[termsList.size()]);

                model.addGenConstrMax(t_plink_kernel, maxTermsArray, 0.0,
                        "t^{plink}_{kernel}=max(\\{b^i_\\{accel\\}\\timest^i_{exec}(accel):i\\inI\\})");

                upperPlinkTime += upperAccelTime;
                // Second, the read and write time
                // Using t^{plink}_{write} = \sum_{(l, k) \in C} (1 - b^l_{accel}) b^k_{accel} n_{(l,k)} bw^{(l, k)}(ca)

                GRBLinExpr writeSumExpr = new GRBLinExpr();
                Double upperTxTime = Double.valueOf(0);
                for (Connection c: C) {

                    // Source instance
                    Instance l = findSourceInstance(c.getSource(), network);

                    // Target instance
                    Instance k = findTargetInstance(c.getTarget(), network);

                    GRBVar b_l_accel = instanceVariables.get(l).get(accelPartition);
                    // b_k_accel
                    GRBVar b_k_accel = instanceVariables.get(k).get(accelPartition);
                    // 1 - b_l_accel

                    GRBVar b_l_accel_neg = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("~b_{%s}_{accel}", l.getInstanceName()));
                    GRBLinExpr expr_b_l_accel_neg = new GRBLinExpr();
                    expr_b_l_accel_neg.addConstant(1.0);
                    expr_b_l_accel_neg.addTerm(-1.0, b_l_accel);

                    model.addConstr(b_l_accel_neg, GRB.EQUAL, expr_b_l_accel_neg, String.format("(1-b_%s_accel)",
                            l.getInstanceName()));

                    GRBVar b_l_accel_neg_and_b_k_accel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("(1-b_%s_accel)b_%s_accel", l.getInstanceName(), k.getInstanceName()));
                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = b_l_accel_neg;
                    argsAnd[1] = b_k_accel;
                    model.addGenConstrAnd(b_l_accel_neg_and_b_k_accel, argsAnd,
                            String.format("constraint:(1-b_%s_accel)b_%s_accel",
                                    l.getInstanceName(), k.getInstanceName()));
                    Double txTime = this.accelDB.getCommunicationTime(c,
                            CommonProfileDataBase.CommunicationTicks.Kind.External);
                    writeSumExpr.addTerm(txTime, b_l_accel_neg_and_b_k_accel);
                    upperTxTime += txTime;

                }

                GRBVar t_plink_write = model.addVar(0.0, upperTxTime, 0.0, GRB.CONTINUOUS,
                        String.format("t_plink_write"));
                model.addConstr(t_plink_write, GRB.EQUAL, writeSumExpr, "t_plink_write=...");

                upperPlinkTime += upperTxTime;
                // Finally, the read time
                // t^{plink}_{read} = \sum_{(l, k) \in C} b^l_{accel}(1 - b^k_{accel}) n_{(l,k)} bw^{(l, k)}(ca)

                GRBLinExpr readSumExpr = new GRBLinExpr();
                Double upperRxTime = Double.valueOf(0);

                for (Connection c: C) {

                    // Source instance
                    Instance l = findSourceInstance(c.getSource(), network);
                    // Target instance
                    Instance k = findTargetInstance(c.getTarget(), network);
                    // b_l_accel
                    GRBVar b_l_accel = instanceVariables.get(l).get(accelPartition);
                    // b_k_accel
                    GRBVar b_k_accel = instanceVariables.get(k).get(accelPartition);

                    // 1 - b_k_accel
                    GRBVar b_k_accel_neg = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("~b_{%s}_{accel}", k.getInstanceName()));

                    GRBLinExpr expr_b_k_accel_neg = new GRBLinExpr();
                    expr_b_k_accel_neg.addConstant(1.0);
                    expr_b_k_accel_neg.addTerm(-1.0, b_k_accel);

                    model.addConstr(b_k_accel_neg, GRB.EQUAL, expr_b_k_accel_neg,
                            String.format("(1-b_%s_accle)", k.getInstanceName()));

                    GRBVar b_k_accel_neg_and_b_l_accel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("(1-b_%s_accel)b_%s_accel", k.getInstanceName(), l.getInstanceName()));
                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = b_l_accel;
                    argsAnd[1] = b_k_accel_neg;

                    model.addGenConstrAnd(b_k_accel_neg_and_b_l_accel, argsAnd,
                            String.format("constraint:(1-b_%s_accel)b_%s_accel",
                                    k.getInstanceName(), l.getInstanceName()));

                    Double rxTime = this.accelDB.getCommunicationTime(c,
                            CommonProfileDataBase.CommunicationTicks.Kind.External);

                    readSumExpr.addTerm(rxTime, b_k_accel_neg_and_b_l_accel);
                    upperRxTime += rxTime;

                }
                GRBVar t_plink_read = model.addVar(0, upperRxTime, 0.0 , GRB.CONTINUOUS,
                        String.format("t_plink_read"));

                model.addConstr(t_plink_read, GRB.EQUAL, readSumExpr, "t_plink_read=...");

                // We assume the PLINK is synchronous
                GRBVar real_T_plink = model.addVar(0.0, upperAccelTime + upperRxTime + upperTxTime,
                        0.0, GRB.CONTINUOUS, String.format("T_plink"));
                GRBLinExpr expr_T_plink = new GRBLinExpr();
                expr_T_plink.addTerm(1.0, t_plink_kernel);
                expr_T_plink.addTerm(1.0, t_plink_read);
                expr_T_plink.addTerm(1.0, t_plink_write);

                upperPlinkTime += upperRxTime;
                T_plink = Optional.of(real_T_plink);
            }


            // We have the time that PLINK requires for execution, now we need to formulate the time spent in each core
            // Note that T_plink in a way represents the time that the accelerator requires for both execution and data
            // transfer

            // The time spent in each core is the sum of the times of individual actors placed on that core

            // The set below is the set of all partitions except the accelerator one
            Set<String> PminusAccel = P.stream()
                    .filter(p -> !p.equals(accelPartition))
                    .collect(Collectors.toSet());
            List<GRBVar> T_list = new ArrayList<>();

            Long upperNetworkTicks = I.stream().map(i -> this.multicoreDB.getInstanceTicks(i))
                    .reduce((i, j) -> i + j).get();
            for (String p : PminusAccel) {
                Double upperCoreTime = upperNetworkTicks.doubleValue();
                GRBLinExpr sumExpr = new GRBLinExpr();
                for (Instance i : I) {

                    Double t_i_exec = this.multicoreDB.getInstanceTicks(i).doubleValue() * this.multicoreClockPeriod;
                    GRBVar b_i_p = instanceVariables.get(i).get(p);
                    sumExpr.addTerm(t_i_exec, b_i_p);

                }
                // Add the T_plink time to the core assigned to plink
                if (p.equals(plinkPartition) && T_plink.isPresent()) {

                    sumExpr.addTerm(1.0, T_plink.get());
                    upperCoreTime += upperPlinkTime;
                }

                GRBVar T_p = model.addVar(0.0, upperCoreTime, 0.0, GRB.CONTINUOUS, String.format("T_%s", p));
                model.addConstr(T_p, GRB.EQUAL, sumExpr, String.format("T_%s=...", p));
                T_list.add(T_p);
            }


            // The total execution time of the network is given by:

            Double upperNetworkTime = upperNetworkTicks.doubleValue()* this.multicoreClockPeriod + upperPlinkTime;
            GRBVar T_network = model.addVar(0.0, upperNetworkTicks, 0.0, GRB.CONTINUOUS, "T_network");
            GRBVar[] T_array = T_list.toArray(new GRBVar[T_list.size()]);
            model.addGenConstrMax(T_network, T_array, 0.0 ,"T_network = max(...)");

            // All it remains is the communication times


            // The first component is the communication time of actors on the same core
            GRBLinExpr expr_T_lc = new GRBLinExpr();
            Double upper_T_lc_p = C.stream()
                    .map(c ->
                            this.multicoreDB
                                    .getCommunicationTicks(c, CommonProfileDataBase.CommunicationTicks.Kind.Local))
                    .reduce(Double::sum).get() * this.multicoreClockPeriod;
            for (String p: PminusAccel) {

                // Tiem of each partition expression
                GRBLinExpr expr_T_lc_p = new GRBLinExpr();

                for (Connection c : C) {

                    Instance j = findSourceInstance(c.getSource(), network);
                    Instance k = findTargetInstance(c.getTarget(), network);
                    GRBVar b_jk_p = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b^{(%s,%s)}_%s", j.getInstanceName(), k.getInstanceName(), p));
                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = instanceVariables.get(j).get(p);
                    argsAnd[1] = instanceVariables.get(k).get(p);
                    model.addGenConstrAnd(b_jk_p, argsAnd, String.format("b^{(%s,%s)}_%s=and(...)",
                            j.getInstanceName(), k.getInstanceName(), p));
                    Double commTime =
                            this.multicoreDB.getCommunicationTicks(c,
                                    CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod;
                    expr_T_lc_p.addTerm(commTime, b_jk_p);
                    upper_T_lc_p += commTime;

                }
                GRBVar T_lc_p = model.addVar(0.0, upper_T_lc_p, 0.0, GRB.CONTINUOUS,
                        String.format("T_lc_%s", p));
                model.addConstr(T_lc_p, GRB.EQUAL, expr_T_lc_p, String.format("T_lc_%s=...", p));
                expr_T_lc.addTerm(1.0, T_lc_p);



            }
            GRBVar T_lc = model.addVar(0.0, upper_T_lc_p, 0.0, GRB.CONTINUOUS, String.format("T_lc"));
            model.addConstr(T_lc, GRB.EQUAL, expr_T_lc, "T_lc=...");

            Optional<GRBVar> T_la = Optional.empty();
            if (this.definedSystemCProfilePath) {

                // The second component is the time spent in accelerator FIFOs
                GRBLinExpr expr_T_la = new GRBLinExpr();
                Double upper_T_la= C.stream()
                        .map(c -> this.accelDB.getCommunicationTime(c, CommonProfileDataBase.CommunicationTicks.Kind.Local))
                        .reduce(Double::sum).get();
                for (Connection c: C) {
                    Instance j = findSourceInstance(c.getSource(), network);
                    Instance k = findTargetInstance(c.getTarget(), network);

                    GRBVar b_jk_accel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b^{(%s, %s)}_accel", j.getInstanceName(), k.getInstanceName()));

                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = instanceVariables.get(j).get(accelPartition);
                    argsAnd[1] = instanceVariables.get(k).get(accelPartition);
                    model.addGenConstrAnd(b_jk_accel, argsAnd,
                            String.format("b^{(%s, %s)}_accel=and(...)", j.getInstanceName(), k.getInstanceName()));
                    Double commTime = this.accelDB.getCommunicationTime(c,
                            CommonProfileDataBase.CommunicationTicks.Kind.Local);
                    expr_T_la.addTerm(commTime, b_jk_accel);
                }
                GRBVar real_T_la = model.addVar(0.0, upper_T_la, 0.0, GRB.CONTINUOUS, "T_la");
                model.addConstr(real_T_la, GRB.EQUAL, expr_T_la, "T_la=...");
                T_la = Optional.of(real_T_la);
            }


            // The third component is the time spent going from one core to another

            Double upper_T_cc_p_q = C.stream().map(
                    c -> this.multicoreDB.getCommunicationTicks(c,
                            CommonProfileDataBase.CommunicationTicks.Kind.External))
                    .reduce(Double::sum).get() * this.multicoreClockPeriod;
            GRBLinExpr expr_T_cc = new GRBLinExpr();
            for (Connection c: C) {

                String connectionName = String.format("{%s.%s->%s.%s}",
                        c.getSource().getInstance().orElse(""), c.getSource().getPort(),
                        c.getTarget().getInstance().orElse(""), c.getTarget().getPort());

                Instance j = findSourceInstance(c.getSource(), network);
                Instance k = findTargetInstance(c.getTarget(), network);

                GRBLinExpr expr_T_cc_c = new GRBLinExpr();
                for (String p : PminusAccel) {

                    Set<String> PminusAccelMinusP = PminusAccel
                            .stream().filter(part -> !part.equals(part)).collect(Collectors.toSet());

                    GRBLinExpr expr_T_cc_c_p= new GRBLinExpr();
                    for (String q : PminusAccelMinusP) {


                        GRBVar b_j_p = instanceVariables.get(j).get(p);
                        GRBVar b_k_q = instanceVariables.get(k).get(q);

                        GRBVar b_jk_pq = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                String.format("b^{(%s,%s)}_{(%s,%s)}", j.getInstanceName(),k.getInstanceName(), p, q));
                        GRBVar[] andArgs = new GRBVar[2];
                        andArgs[0] = b_j_p;
                        andArgs[1] = b_k_q;
                        model.addGenConstrAnd(b_jk_pq, andArgs,
                                String.format("b^{(%s,%s)}_{(%s,%s)}=and(...)", j.getInstanceName(),k.getInstanceName(), p, q));
                        Double commTime = this.multicoreDB.getCommunicationTicks(c,
                                CommonProfileDataBase.CommunicationTicks.Kind.External);

                        expr_T_cc_c_p.addTerm(commTime, b_jk_pq);

                    }

                    GRBVar T_cc_c_p =  model.addVar(0.0, upper_T_cc_p_q, 0.0, GRB.CONTINUOUS,
                            String.format("T_cc_%s_%s", connectionName, p));
                    model.addConstr(T_cc_c_p, GRB.EQUAL, expr_T_cc_c_p, String.format("T_cc_%s_%s=...",
                            connectionName, p));


                    expr_T_cc_c.addTerm(1.0, T_cc_c_p);
                }

                GRBVar T_cc_c = model.addVar(0.0, upper_T_cc_p_q, 0.0, GRB.CONTINUOUS,
                        String.format("T_cc_%s", connectionName));
                model.addConstr(T_cc_c, GRB.EQUAL, expr_T_cc_c,
                        String.format("T_cc_%s=...", connectionName));

                expr_T_cc.addTerm(1.0, T_cc_c);
            }

            GRBVar T_cc = model.addVar(0.0, upper_T_cc_p_q, 0.0, GRB.CONTINUOUS,
                    String.format("T_cc"));
            model.addConstr(T_cc, GRB.EQUAL, expr_T_cc, "T_cc=...");

            // And the last piece of the puzzle is the time spent communicating with the PLINK actor
            // Note that the tim spent to send data over PCI-e is modelled by the plink rw time

            Optional<GRBVar> T_ca = Optional.empty();
            if (this.definedSystemCProfilePath) {

                GRBLinExpr expr_T_ca = new GRBLinExpr();
                for (Connection c: C) {

                    Instance j = findSourceInstance(c.getSource(), network);
                    Instance k = findSourceInstance(c.getTarget(), network);

                    GRBVar b_j_plink = instanceVariables.get(j).get(plinkPartition);
                    GRBVar b_k_accel = instanceVariables.get(k).get(accelPartition);
                    GRBVar b_j_accel = instanceVariables.get(j).get(accelPartition);
                    GRBVar b_k_plink = instanceVariables.get(k).get(plinkPartition);



                    GRBVar b_jk_plinkaccel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b_(%s,%s)_(accel,plink)", j.getInstanceName(), k.getInstanceName()));
                    GRBVar b_jk_accelplink = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b_(%s,%s)_(accel,plink)", j.getInstanceName(), k.getInstanceName()));

                    GRBVar[] args_b_jk_plinkaccel = new GRBVar[2];
                    args_b_jk_plinkaccel[0] = b_j_plink;
                    args_b_jk_plinkaccel[1] = b_k_accel;
                    GRBVar[] args_b_jk_accelplink = new GRBVar[2];
                    args_b_jk_accelplink[0] = b_j_accel;
                    args_b_jk_accelplink[1] = b_k_plink;

                    model.addGenConstrAnd(b_jk_plinkaccel, args_b_jk_plinkaccel,
                            String.format("b_(%s,%s)_(plink,accel)=and(...)",
                                    j.getInstanceName(), k.getInstanceName()));

                    model.addGenConstrAnd(b_jk_accelplink, args_b_jk_accelplink,
                            String.format("b_(%s,%s)_(accel,plink)=and(...)",
                                    j.getInstanceName(), k.getInstanceName()));
                    Double localTime =
                            this.multicoreDB.getCommunicationTicks(c,
                                    CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod;
                    Double globalTime =
                            this.multicoreDB.getCommunicationTicks(c,
                                    CommonProfileDataBase.CommunicationTicks.Kind.External) * this.multicoreClockPeriod;

                    expr_T_ca.addTerm(localTime, b_jk_accelplink);
                    expr_T_ca.addTerm(localTime, b_jk_accelplink);


                    Set<String> PminusPlinkMinusAccel =
                            PminusAccel.stream().filter(p -> !p.equals(plinkPartition)).collect(Collectors.toSet());
                    GRBLinExpr expr_T_ca_c = new GRBLinExpr();
                    for (String p: PminusPlinkMinusAccel) {

                        GRBVar b_j_p = instanceVariables.get(j).get(p);
                        GRBVar b_k_p = instanceVariables.get(k).get(p);

                        GRBVar b_jk_paccel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                String.format("b_(%s,%s)_(%s,accel)", j.getInstanceName(), k.getInstanceName(), p));

                        GRBVar b_jk_accelp = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                String.format("b_(%s,%s)_(accel,%s)", j.getInstanceName(), k.getInstanceName(), p));

                        GRBVar[] args_b_jk_paccel = new GRBVar[2];
                        GRBVar[] args_b_jk_accelp = new GRBVar[2];
                        args_b_jk_paccel[0] = b_j_p;
                        args_b_jk_paccel[1] = b_k_accel;
                        args_b_jk_accelp[0] = b_j_accel;
                        args_b_jk_accelp[1] = b_k_p;
                        model.addGenConstrAnd(b_jk_paccel, args_b_jk_paccel,
                                String.format("b_(%s,%s)_(%s,accel)=and(...)",
                                        j.getInstanceName(), k.getInstanceName(), p));
                        model.addGenConstrAnd(b_jk_accelp, args_b_jk_accelp,
                                String.format("b_(%s,%s)_(accel,%s)", j.getInstanceName(), k.getInstanceName(), p));

                        expr_T_ca_c.addTerm(globalTime, b_jk_paccel);
                        expr_T_ca_c.addTerm(globalTime, b_jk_accelp);
                    }
                    String connectionName = String.format("{%s.%s->%s.%s}",
                            c.getSource().getInstance().orElse(""), c.getSource().getPort(),
                            c.getTarget().getInstance().orElse(""), c.getTarget().getPort());
                    GRBVar T_ca_c = model.addVar(0.0, upper_T_cc_p_q, 0.0, GRB.CONTINUOUS,
                            String.format("T_ca_%s", connectionName));
                    model.addConstr(T_ca_c, GRB.EQUAL, expr_T_ca_c,
                            String.format("T_ca_%s=...", connectionName));

                    expr_T_ca.addTerm(1.0, T_ca_c);
                }
                GRBVar real_T_ca = model.addVar(0.0, upper_T_cc_p_q + upper_T_lc_p, 0.0, GRB.CONTINUOUS,
                        "T_ca");
                model.addConstr(real_T_ca, GRB.EQUAL, expr_T_ca, "T_ca=...");

                T_ca = Optional.of(real_T_ca);

            }


            objectiveExpr.addTerm(1.0, T_network);
            objectiveExpr.addTerm(1.0, T_lc);
            objectiveExpr.addTerm(1.0, T_cc);
            if (T_la.isPresent())
                objectiveExpr.addTerm(1.0, T_la.get());
            if (T_ca.isPresent())
                objectiveExpr.addTerm(1.0, T_ca.get());

            model.setObjective(objectiveExpr, GRB.MINIMIZE);

            Path modelFile = logPath.resolve("model.lp");
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Writing model into " + modelFile.toString()));
            model.write(modelFile.toString());


            model.presolve();
            // Find a solution
            model.optimize();

            for (String p : P) {
                partitions.put(p, new ArrayList<Instance>());
            }
            for (Instance i : I) {

                String bestPartition = plinkPartition;
                Double bestPartitionValue = Double.valueOf(0);
                for (String p : P) {

                    GRBVar b_i_p = instanceVariables.get(i).get(p);
                    if (b_i_p.get(GRB.DoubleAttr.X) > bestPartitionValue) {
                        bestPartition = p;
                        bestPartitionValue = b_i_p.get(GRB.DoubleAttr.X);
                    }
                }
                partitions.get(bestPartition).add(i);

            }


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

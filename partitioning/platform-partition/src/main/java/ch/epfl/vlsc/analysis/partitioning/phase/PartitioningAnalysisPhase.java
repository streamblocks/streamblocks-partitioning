package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.parser.*;
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
import java.net.Inet4Address;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PartitioningAnalysisPhase implements Phase {

    Context context;
    CompilationTask task;
    MulticoreProfileDataBase multicoreDB;
    DeviceProfileDataBase accelDB;

    Double multicoreClockPeriod; // in NS
    Double accelClockPeriod; // in NS


    boolean definedMulticoreProfilePath;
    boolean definedSystemCProfilePath;

    boolean definedOclProfilePath;
    boolean definedCoreCommProfilePath;

    private final String plinkPartition;
    private final String accelPartition;
    GRBModel model;
    public PartitioningAnalysisPhase() {
        this.multicoreDB = null;
        this.multicoreClockPeriod = 1.0 / 3.4; // 3.4 GHz
        this.accelDB = null;
        this.accelClockPeriod = 1.0 / .185; // 280 MHz
        this.definedCoreCommProfilePath = false;
        this.definedOclProfilePath = false;
        this.definedSystemCProfilePath = false;
        this.definedMulticoreProfilePath = false;


        this.plinkPartition = "core_0";
        this.accelPartition = "accel";

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


        this.context = context;
        this.task = task;
        getRequiredSettings(task, context);
        MulticoreProfileParser multicoreParser = new MulticoreProfileParser(task, context, this.multicoreClockPeriod);
        multicoreParser.parseExecutionProfile(context.getConfiguration().get(PartitionSettings.multiCoreProfilePath));
        if (this.definedCoreCommProfilePath) {
            multicoreParser.parseBandwidthProfile(context.getConfiguration().get(PartitionSettings.multicoreCommunicationProfilePath));
        }
        DeviceProfileParser devParser = new DeviceProfileParser(task, context);

        if (this.definedSystemCProfilePath) {
            devParser.parseExecutionProfile(context.getConfiguration().get(PartitionSettings.systemCProfilePath));
            if (this.definedOclProfilePath) {
                devParser.parseBandwidthProfile(context.getConfiguration().get(PartitionSettings.openCLProfilePath));
            }
        }
        this.multicoreDB = multicoreParser.getDataBase();
        this.accelDB = devParser.getDataBase();
        Long startTime = System.currentTimeMillis();

        ImmutableList<Map<String, List<Instance>>> solutions = solveModel(task, context);
        Long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.printf("Found solution in %d ms.\n", elapsedTime);
        int id = 0;
        for(Map<String, List<Instance>> partitions: solutions) {

            try {
                model.set(GRB.IntParam.SolutionNumber, id);
                Double estimated_time = model.getVarByName("T").get(GRB.DoubleAttr.Xn) * 1e-6;
                System.out.printf("Solution %d (%f ms)\n", id, estimated_time);
                reportPartition(partitions);
                printTimingBreakdown(id);
                createConfig(partitions, context, id);
                id++;
            } catch (GRBException e) {
                e.printStackTrace();
            }


        }
        return task;
    }

    private void printTimingBreakdown(int id) throws GRBException{

        Double T_total = getVarByName("T");
        Double T_network = getVarByName("T_network");
        Double T_lc = getVarByName("T_lc");
        Double T_cc = getVarByName("T_cc");
        printVar("T");
        printVar("T_network");
        printVar("T_lc");
        printVar("T_cc");
        if (this.definedSystemCProfilePath && this.definedOclProfilePath) {

//            printVar("T_la");
            printVar("T_ca");
            printVar("t_plink_kernel");
            printVar("t_plink_read");
            printVar("t_plink_write");

        }


    }

    private Double getVarByName(String name) throws GRBException {
        return model.getVarByName(name).get(GRB.DoubleAttr.Xn) * 1e-6;
    }
    private void printVar(String name, Double value) {
        System.out.printf("%s = %6.6f ms\n", name, value);
    }
    private void printVar(String name) throws GRBException{
        printVar(name, getVarByName(name));
    }
    private void reportPartition(Map<String, List<Instance>> partitions) {
        for (String p: partitions.keySet()) {
            System.out.printf("\t%s: ", p);
            for (Instance instance: partitions.get(p)) {
                System.out.printf("\t%s", instance.getInstanceName());
            }
            System.out.printf("\n");
        }
    }

    /**
     * Return the multicore partition id from a partition string core_id
     * @param partition
     * @return
     */
    private String getMulticorePartitionId(String partition) {
        return partition.substring(5);
    }
    private void createConfig(Map<String, List<Instance>> partitions, Context context, int pid) {

        Path configPath =
            context.getConfiguration().isDefined(PartitionSettings.configPath) ?
                context.getConfiguration().get(PartitionSettings.configPath) :
                    context.getConfiguration().get(Compiler.targetPath).resolve("bin/config_" + pid + ".xml");

        try {
            // the config xml doc
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element configRoot = doc.createElement("Configuration");
            doc.appendChild(configRoot);
            Element partitioningRoot = doc.createElement("Partitioning");
            configRoot.appendChild(partitioningRoot);
            Integer id = 0;
            for (String partition: partitions.keySet()) {
                List<Instance> instances = partitions.get(partition);
                if (!instances.isEmpty()) {

                    Element partitionRoot = doc.createElement("Partition");

                    partitionRoot.setAttribute("id", id.toString());
                    instances.forEach( instance -> {
                        Element instanceRoot = doc.createElement("Instance");
                        instanceRoot.setAttribute("actor-id", instance.getInstanceName());
                        partitionRoot.appendChild(instanceRoot);
                    });
                    partitioningRoot.appendChild(partitionRoot);
                    id = id + 1;
                }
            }

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

    private void printStatistics() {

        LinkedHashMap<Instance, Long> softwareInstances =
                this.multicoreDB.getExecutionProfileDataBase().entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue())
                        .collect(
                                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        System.out.println("Software actor stats:");
        System.out.println("Actor execution stats:\t\tSoftware\t\tHardware (ms)");

        for (Instance instance: this.task.getNetwork().getInstances()) {
            Double sw = this.multicoreDB.getInstanceTicks(instance) * this.multicoreClockPeriod * 1e-6;
            Double hw = this.accelDB.getInstanceTicks(instance) * this.accelClockPeriod * 1e-6;
            System.out.printf("%20s:\t\t%06.4f\t\t%06.4f\t\t(ms)\n",instance.getInstanceName(), sw, hw);
        }


    }
    private ImmutableList<Map<String, List<Instance>>> solveModel(CompilationTask task, Context context) {
        Network network = task.getNetwork();
        printStatistics();
        try {

            GRBEnv env = new GRBEnv(true);
            Path logPath = context.getConfiguration().get(Compiler.targetPath);
            Path logfile = logPath.resolve("partitions.log");

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Logging into " + logfile.toString()));

            env.set("LogFile", logfile.toString());


            env.start();

            model = new GRBModel(env);

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
                        String.format("%s_unique_partition", i.getInstanceName()));
                // Make some instances software only
                if (this.definedSystemCProfilePath && !this.accelDB.getExecutionProfileDataBase().contains(i)) {
                    GRBVar b_i_accel = instanceVariables.get(i).get(accelPartition);
                    model.addConstr(b_i_accel, GRB.EQUAL, 0.0,
                            String.format("%s_software_only", i.getInstanceName()));
                }

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



                // Get the local FPGA FIFO time


                Double upper_T_la=
                        Collections.max(C.map(c -> this.multicoreDB.getTokensExchanged(c) * this.accelClockPeriod));
                List<GRBVar> fifoTimes = new ArrayList<>();
                for (Connection c: C) {
                    Instance j = findSourceInstance(c.getSource(), network);
                    Instance k = findTargetInstance(c.getTarget(), network);

                    GRBVar b_jk_accel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b^{(%s,%s)}_accel", j.getInstanceName(), k.getInstanceName()));

                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = instanceVariables.get(j).get(accelPartition);
                    argsAnd[1] = instanceVariables.get(k).get(accelPartition);
                    model.addGenConstrAnd(b_jk_accel, argsAnd,
                            String.format("b^{(%s,%s)_%s}_accel=and(...)",
                                    j.getInstanceName(), k.getInstanceName(), getConnectionName(c)));
                    // Time spent in FIFOs is modelled as tokens * clock, since FIFOs have a bandwidth of
                    // one token per cycle.
                    Long tokens = this.multicoreDB.getTokensExchanged(c);
                    Double commTime = tokens.doubleValue() * this.accelClockPeriod ;
                    GRBVar T_la_c = model.addVar(0.0, upper_T_la, 0.0, GRB.CONTINUOUS,
                            String.format("T_la_%s", getConnectionName(c)));
                    GRBLinExpr T_la_c_expr = new GRBLinExpr();
                    T_la_c_expr.addTerm(commTime, b_jk_accel);
                    model.addConstr(T_la_c, GRB.EQUAL, T_la_c_expr,
                            String.format("T_la_%s=...", getConnectionName(c)));
                    fifoTimes.add(T_la_c);

                    System.out.printf("Accel FIFO time %s: %6.6f \n", getConnectionName(c), commTime * 1e-6);
                    //expr_T_la.addTerm(commTime, b_jk_accel);
                }
//                GRBVar real_T_la = model.addVar(0.0, upper_T_la, 0.0, GRB.CONTINUOUS, "T_la");
//                GRBVar[] maxTermsArray = fifoTimes.toArray(new GRBVar[fifoTimes.size()]);
//                model.addGenConstrMax(real_T_la, maxTermsArray, 0.0, "T_la=max(..)");


                // First off, the kernel time
                // terms list for t^{plink}_{kernel} = max(\{b^i_\{accel\} \times t^i_{exec}(accel): i \in I\})
                List<GRBVar> actorTimes = new ArrayList<>();
                for (Instance i : I) {

                    Double t_i_exec_accel = this.accelDB.getInstanceTicks(i).doubleValue() * this.accelClockPeriod;

                    GRBVar b_i_accel_t_i_exec_product =  model.addVar(0.0,
                            t_i_exec_accel, 0.0, GRB.CONTINUOUS,
                            String.format("t^%s_{exec}(accel)", i.getInstanceName()));
                    GRBLinExpr expr = new GRBLinExpr();

                    // The time of each instance on the FPGA =  ticks * clockPeriod

                    GRBVar b_i_accel = instanceVariables.get(i).get(accelPartition);
                    expr.addTerm(t_i_exec_accel, b_i_accel);
                    model.addConstr(expr, GRB.EQUAL, b_i_accel_t_i_exec_product,
                            String.format("t^%s_{exec}(accel)=...",
                                    i.getInstanceName()));
                    actorTimes.add(b_i_accel_t_i_exec_product);
                }

                List<GRBVar> accelTimes = new ArrayList<>();
                accelTimes.addAll(actorTimes);
                accelTimes.addAll(fifoTimes);

                // The upper bound on the accel partion is the maximum of all instance times, while the upper
                // bound on a multicore partition is the sum of all instance times
                Double upperAccelTime = Collections.max(
                        this.accelDB.getExecutionProfileDataBase().values())
                        .doubleValue() * this.accelClockPeriod;
                upperAccelTime = upperAccelTime > upper_T_la ? upperAccelTime : upper_T_la;

                GRBVar t_plink_kernel = model.addVar(0.0, upperAccelTime, 0.0, GRB.CONTINUOUS,
                        "t_plink_kernel");

                GRBVar[] maxTermsArray = accelTimes.toArray(new GRBVar[accelTimes.size()]);

                context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO,
                        String.format("Maximum t_plink_kernel = %6.6f ms\n", upperAccelTime * 1e-6)));


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

                    model.addConstr(b_l_accel_neg, GRB.EQUAL, expr_b_l_accel_neg, String.format("~b_%s_(accel)",
                            l.getInstanceName()));

                    GRBVar b_l_accel_neg_and_b_k_accel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b^{~%s,%s}_{accel}", l.getInstanceName(), k.getInstanceName()));
                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = b_l_accel_neg;
                    argsAnd[1] = b_k_accel;
                    model.addGenConstrAnd(b_l_accel_neg_and_b_k_accel, argsAnd,
                            String.format("b^{~%s,%s}_%s_{accel}=(1-b_%1$s_accel)b_%2$s_accel",
                                    l.getInstanceName(), k.getInstanceName(), getConnectionName(c)));
                    Long bufferSize = Long.valueOf(this.multicoreDB.getConnectionBytes(c));
                    Long tokens = this.multicoreDB.getTokensExchanged(c);
                    Double txTime = this.accelDB.getPCIeWriteTime(bufferSize, tokens);

                    System.out.printf("PCIe write for %s: %6.6f ms\n", getConnectionName(c), txTime * 1e-6);
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
                            String.format("~b_{%s}_{accel}=(1-b_%1$s_accl)", k.getInstanceName()));

                    GRBVar b_k_accel_neg_and_b_l_accel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b^{%s,~%s}_%s_{accel}", l.getInstanceName(), k.getInstanceName(),
                                    getConnectionName(c)));
                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = b_l_accel;
                    argsAnd[1] = b_k_accel_neg;

                    model.addGenConstrAnd(b_k_accel_neg_and_b_l_accel, argsAnd,
                            String.format("b^{%s,~%s}_%s_{accel}=and(...)",
                                    l.getInstanceName(), k.getInstanceName(), getConnectionName(c)));
                    Long bufferSize = Long.valueOf(this.multicoreDB.getConnectionBytes(c));
                    Long tokens = this.multicoreDB.getTokensExchanged(c);
                    Double rxTime = this.accelDB.getPCIeReadTime(bufferSize, tokens);
                    System.out.printf("PCIe read for %s: %6.6f ms\n", getConnectionName(c), rxTime * 1e-6);
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
                model.addConstr(real_T_plink, GRB.EQUAL, expr_T_plink,
                        "T_plink=t_plink_kernel+t_plink_read+t_plink_write");
                upperPlinkTime += upperRxTime;
                T_plink = Optional.of(real_T_plink);
            }


            // We have the time that PLINK requires for execution, now we need to formulate the time spent in each core
            // Note that T_plink in a way represents the time that the accelerator requires for both execution and data
            // transfer

            // The time spent in each core is the sum of the times of individual actors placed on that core

            // The set below is the set of all partitions except the accelerator
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
            GRBVar T_network = model.addVar(0.0, upperNetworkTime, 0.0, GRB.CONTINUOUS, "T_network");
            GRBVar[] T_array = T_list.toArray(new GRBVar[T_list.size()]);
            model.addGenConstrMax(T_network, T_array, 0.0 ,"T_network = max(...)");

            // All it remains is the communication times


            // The first component is the communication time of actors on the same core
//            GRBLinExpr expr_T_lc = new GRBLinExpr();
            Double upper_T_lc_p = C.stream()
                    .map(c ->
                            this.multicoreDB
                                    .getCommunicationTicks(c, CommonProfileDataBase.CommunicationTicks.Kind.Local))
                    .reduce(Double::sum).get() * this.multicoreClockPeriod;
            List<GRBVar> T_lc_p_list = new ArrayList<>();
            for (String p: PminusAccel) {

                // Tiem of each partition expression
                GRBLinExpr expr_T_lc_p = new GRBLinExpr();

                for (Connection c : C) {

                    Instance j = findSourceInstance(c.getSource(), network);
                    Instance k = findTargetInstance(c.getTarget(), network);
                    GRBVar b_jk_p = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b^%s_%s", getConnectionName(c), p));
                    GRBVar[] argsAnd = new GRBVar[2];
                    argsAnd[0] = instanceVariables.get(j).get(p);
                    argsAnd[1] = instanceVariables.get(k).get(p);
                    model.addGenConstrAnd(b_jk_p, argsAnd, String.format("b^%s_%s=and(...)", getConnectionName(c), p));
                    Double commTime =
                            this.multicoreDB.getCommunicationTicks(c,
                                    CommonProfileDataBase.CommunicationTicks.Kind.Local) * this.multicoreClockPeriod;

                    expr_T_lc_p.addTerm(commTime, b_jk_p);


                }
                GRBVar T_lc_p = model.addVar(0.0, upper_T_lc_p, 0.0, GRB.CONTINUOUS,
                        String.format("T_lc_%s", p));
                model.addConstr(T_lc_p, GRB.EQUAL, expr_T_lc_p, String.format("T_lc_%s=...", p));
                T_lc_p_list.add(T_lc_p);
//                expr_T_lc.addTerm(1.0, T_lc_p);



            }
            GRBVar[] T_lc_p_array = T_lc_p_list.toArray(new GRBVar[T_lc_p_list.size()]);
            GRBVar T_lc = model.addVar(0.0, upper_T_lc_p, 0.0, GRB.CONTINUOUS, String.format("T_lc"));
//            model.addConstr(T_lc, GRB.EQUAL, expr_T_lc, "T_lc=...");
            model.addGenConstrMax(T_lc, T_lc_p_array, 0.0, String.format("T_lc=max(..)"));



            // The third component is the time spent going from one core to another

            Double upper_T_cc_p_q = C.stream().map(
                    c -> this.multicoreDB.getCommunicationTicks(c,
                            CommonProfileDataBase.CommunicationTicks.Kind.Global))
                    .reduce(Double::sum).get() * this.multicoreClockPeriod;
            GRBLinExpr expr_T_cc = new GRBLinExpr();
            for (Connection c: C) {

//                String connectionName = String.format("{%s.%s->%s.%s}",
//                        c.getSource().getInstance().orElse(""), c.getSource().getPort(),
//                        c.getTarget().getInstance().orElse(""), c.getTarget().getPort());
                String connectionName = getConnectionName(c);
                Instance j = findSourceInstance(c.getSource(), network);
                Instance k = findTargetInstance(c.getTarget(), network);

                GRBLinExpr expr_T_cc_c = new GRBLinExpr();
                for (String p : PminusAccel) {

                    Set<String> PminusAccelMinusP = PminusAccel
                            .stream().filter(part -> !part.equals(p)).collect(Collectors.toSet());

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
                                CommonProfileDataBase.CommunicationTicks.Kind.Global) * this.multicoreClockPeriod;

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
                    Instance k = findTargetInstance(c.getTarget(), network);

                    GRBVar b_j_plink = instanceVariables.get(j).get(plinkPartition);
                    GRBVar b_k_accel = instanceVariables.get(k).get(accelPartition);
                    GRBVar b_j_accel = instanceVariables.get(j).get(accelPartition);
                    GRBVar b_k_plink = instanceVariables.get(k).get(plinkPartition);



                    GRBVar b_jk_plinkaccel = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("b_(%s,%s)_(plink,accel)", j.getInstanceName(), k.getInstanceName()));
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
                                    CommonProfileDataBase.CommunicationTicks.Kind.Global) * this.multicoreClockPeriod;

                    expr_T_ca.addTerm(localTime, b_jk_plinkaccel);
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

            if (T_ca.isPresent())
                objectiveExpr.addTerm(1.0, T_ca.get());

            GRBVar T = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "T");
            model.addConstr(T, GRB.EQUAL, objectiveExpr, "T=..");

            model.setObjective(objectiveExpr, GRB.MINIMIZE);


            Path modelFile = logPath.resolve("model.lp");
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Writing model into " + modelFile.toString()));
            model.write(modelFile.toString());


            model.presolve();
            // Find all solutions
            model.optimize();

            ImmutableList.Builder<Map<String, List<Instance>>> solBuilder = ImmutableList.builder();

            int solutionCount = model.get(GRB.IntAttr.SolCount);

            for (int solId = 0; solId < solutionCount; solId++) {

                model.set(GRB.IntParam.SolutionNumber, solId);

                solBuilder.add(makePartitionMap(instanceVariables, P, I));
            }
            return solBuilder.build();

        } catch (GRBException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format("GRB exception caught with error %s. %s",
                                    e.getErrorCode(), e.getMessage())));

        }

    }


    private Map<String, List<Instance>> makePartitionMap(Map<Instance, InstancePartitionVariables> instanceVariables,
                                                        Set<String> P, ImmutableList<Instance> I) {
        Map<String, List<Instance>> partitions = new HashMap<>();
        try{
            for (String p : P) {
                partitions.put(p, new ArrayList<Instance>());
            }
            for (Instance i : I) {

                String bestPartition = plinkPartition;
                Double bestPartitionValue = Double.valueOf(0);
                for (String p : P) {

                    GRBVar b_i_p = instanceVariables.get(i).get(p);
                    if (b_i_p.get(GRB.DoubleAttr.Xn) > bestPartitionValue) {
                        bestPartition = p;
                        bestPartitionValue = b_i_p.get(GRB.DoubleAttr.Xn);

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
    private String getConnectionName(Connection c) {

        return String.format("{%s.%s->%s.%s}",
                c.getSource().getInstance().orElse(""), c.getSource().getPort(),
                c.getTarget().getInstance().orElse(""), c.getTarget().getPort());

    }




}

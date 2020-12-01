package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.models.HeterogeneousModel;
import ch.epfl.vlsc.analysis.partitioning.models.MulticorePerformanceModel;
import ch.epfl.vlsc.analysis.partitioning.models.PerformanceModel;
import ch.epfl.vlsc.analysis.partitioning.models.PinnedHardwareModel;
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
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;

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
    Double timeLimit;

    boolean definedMulticoreProfilePath;
    boolean definedSystemCProfilePath;

    boolean definedOclProfilePath;
    boolean definedCoreCommProfilePath;

    private final String plinkPartition;
    private final String accelPartition;
    GRBModel model;
    public PartitioningAnalysisPhase() {
        this.multicoreDB = null;
        this.multicoreClockPeriod = 1.0 / 3.4 * 1e-9; // 3.4 GHz
        this.accelDB = null;
        this.accelClockPeriod = 1.0 / .185 * 1e-9; // 280 MHz
        this.definedCoreCommProfilePath = false;
        this.definedOclProfilePath = false;
        this.definedSystemCProfilePath = false;
        this.definedMulticoreProfilePath = false;

        this.timeLimit = 300.0;

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
                PartitionSettings.cpuCoreCount,
                PartitionSettings.searchMode);
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

        int maxCores = context.getConfiguration().get(PartitionSettings.cpuCoreCount);



//        PinnedHardwareModel perfModel = new PinnedHardwareModel(
//                task, context, multicoreDB, accelDB, multicoreClockPeriod, accelClockPeriod, 300.0
//        );
////        MulticorePerformanceModel perfModel = new MulticorePerformanceModel(
////                task, context, multicoreDB, multicoreClockPeriod, 300.0
////        );
//        Path logPath = context.getConfiguration().get(Compiler.targetPath);
//
//        maxCores = Math.min(perfModel.getMaxPartitions(), maxCores);
//        for (int cores = 2; cores <= maxCores; cores ++) {
////            printStatistics();
////            perfModel.reportHardwareConsumptionProductions();
//            System.out.println("Hardware kernel time: " + perfModel.getPlinkKernelCost2() + ", " + perfModel.getPlinkKernelCost());
//            System.out.println("Plink read: " + perfModel.getPlinkReadCost() + ", Plink write: " + perfModel.getPlinkWriteCost());
//            ImmutableList<PerformanceModel.PartitioningSolution<String>> solutions = perfModel.solveModel(cores);
//            File configDir = logPath.resolve(String.valueOf(cores)).toFile();
//            if (!configDir.exists()) {
//                configDir.mkdirs();
//            }
//            System.out.println("Solved the model for " + cores + " cores");
//            perfModel.solutionsSummary(configDir);
//            for (PerformanceModel.PartitioningSolution<String> solution: solutions) {
//
//                perfModel.dumpMulticoreConfig(
//                        configDir + "/config_" + solutions.indexOf(solution) + ".xml",
//                        solution);
//            }
//
//        }

        HeterogeneousModel perfModel = new HeterogeneousModel(
                task, context, multicoreDB, accelDB, multicoreClockPeriod, accelClockPeriod, 300.0
        );
        printStatistics();

        perfModel.solveModel(maxCores);


//        for (int cores = 2; cores <= maxCores; cores++) {
//
//            Long startTime = System.currentTimeMillis();
//
//            ImmutableList<Map<String, List<Instance>>> solutions = solveModel(task, context, cores);
//            Long elapsedTime = System.currentTimeMillis() - startTime;
//            System.out.printf("Found solution in %d s.\n", elapsedTime);
//            int id = 0;
//
//            Path dumpPath =
//                    context.getConfiguration().isDefined(PartitionSettings.configPath) ?
//                            context.getConfiguration().get(PartitionSettings.configPath) :
//                            context.getConfiguration().get(Compiler.targetPath).resolve("bin/" + cores);
//
//
//            File dumpDir = new File(dumpPath.toUri());
//
//            if (!dumpDir.exists())
//                dumpDir.mkdirs();
//
//            File dumpFile = new File(dumpDir + "/solutions.csv");
//
//            try {
//                PrintWriter solutionWriter = new PrintWriter(dumpFile);
//                solutionWriter.println("T,T_network,T_lc,T_cc,T_ca,T_plink_kernel,T_plink_read,T_plink_write");
//                for (Map<String, List<Instance>> partitions : solutions) {
//
//                    try {
//                        model.set(GRB.IntParam.SolutionNumber, id);
//                        Double estimated_time = model.getVarByName("T").get(GRB.DoubleAttr.Xn);
//                        System.out.printf("Solution %d (%f s)\n", id, estimated_time);
//                        reportPartition(partitions);
//
//                        printTimingBreakdown(id, solutionWriter);
//                        createConfig(partitions, context, id, cores);
//                        id++;
//                    } catch (GRBException e) {
//                        context.getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, "Could not get solution " + id));
//                        e.printStackTrace();
//                    }
//
//                }
//                solutionWriter.close();
//            } catch (FileNotFoundException e) {
//                context.getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, "Could not create file " + dumpFile.toString()));
//            }
//        }





        return task;
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
            Double sw = this.multicoreDB.getInstanceTicks(instance) * this.multicoreClockPeriod * 1e3;
            Double hw = this.accelDB.getInstanceTicks(instance) * this.accelClockPeriod * 1e3;
            System.out.printf("%20s:\t\t%06.4f\t\t%06.4f\t\t(ms)\n",instance.getInstanceName(), sw, hw);
        }


    }




}

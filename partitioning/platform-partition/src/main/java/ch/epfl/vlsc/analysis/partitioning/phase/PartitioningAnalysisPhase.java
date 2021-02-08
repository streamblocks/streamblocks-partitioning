package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.models.HeterogeneousModel;
import ch.epfl.vlsc.analysis.partitioning.models.MulticorePerformanceModel;
import ch.epfl.vlsc.analysis.partitioning.models.PerformanceModel;

import ch.epfl.vlsc.analysis.partitioning.parser.*;
import ch.epfl.vlsc.analysis.partitioning.util.PartitionSettings;

import ch.epfl.vlsc.analysis.partitioning.util.SolutionIdentity;
import com.google.gson.*;
import gurobi.*;


import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;


import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.Setting;
import se.lth.cs.tycho.compiler.Compiler;

import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
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
        this.multicoreClockPeriod = 1.0 / 3.3 * 1e-9; // 3.3 GHz
        this.accelDB = null;
        this.accelClockPeriod = 1.0 / .250 * 1e-9; // 250 MHz
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




        printStatistics();

        PartitionSettings.Mode mode = context.getConfiguration().get(PartitionSettings.searchMode);
        Path logPath = context.getConfiguration().get(Compiler.targetPath).resolve("homogeneous");

        if (mode == PartitionSettings.Mode.HOMOGENEOUS) {
            context.getReporter().report(new Diagnostic(
                    Diagnostic.Kind.INFO, "HOMOGENEOUS PARTITIONING MODE"
            ));

            MulticorePerformanceModel perfModel = new MulticorePerformanceModel(
                    task, context, multicoreDB, multicoreClockPeriod,  300.0
            );

            maxCores = Math.min(perfModel.getMaxPartitions(), maxCores);

            for (int cores = 2; cores <= maxCores; cores++) {
                ImmutableList<PerformanceModel.PartitioningSolution<String>> solutions = perfModel.solveModel(cores);
                File configDir = logPath.resolve(String.valueOf(cores)).toFile();
                if (!configDir.exists()) {
                    configDir.mkdirs();
                }
                System.out.println("Solved the model for " + cores + " cores");
                perfModel.solutionsSummary(configDir);
                for (PerformanceModel.PartitioningSolution<String> solution: solutions) {

                    perfModel.dumpMulticoreConfig(
                            configDir + "/config_" + solutions.indexOf(solution) + ".xml",
                            solution, multicoreDB);
                }
            }

        } else if (mode == PartitionSettings.Mode.HETEROGENEOUS) {
            context.getReporter().report(new Diagnostic(
                    Diagnostic.Kind.INFO, "HETEROGENEOUS PARTITIONING MODE"
            ));
            HeterogeneousModel perfModel = new HeterogeneousModel(
                    task, context, multicoreDB, accelDB, multicoreClockPeriod, accelClockPeriod, 300.0
            );

            Map<SolutionIdentity, Integer> solutionToUniqueHwMap = new TreeMap<>(
                    (t1, t2) -> {
                        if (t1.numberOfCores < t2.numberOfCores) {
                            return -1;
                        } else if (t1.numberOfCores > t2.numberOfCores) {
                            return 1;
                        } else {
                            return t1.solutionNumber - t2.solutionNumber;
                        }
                    }
            );
            Set<Set<String>> uniqueHardwarePartitions = new HashSet<>();
            for (int cores = 1; cores <= maxCores; cores++) {

                ImmutableList<PerformanceModel.PartitioningSolution<String>> solutions = perfModel.solveModel(cores);

                // Find the hardware partition for every solution and add the hardware partition to the
                // set of unique hardware partitions and update a map from solution ids to unique hw partitions
                // sets
                int newSolutions = 0;
                for(PerformanceModel.PartitioningSolution<String> sol : solutions) {
                    int solNumber = solutions.indexOf(sol);
                    Set<String> hwActors = sol.getPartitions().stream()
                            .filter(p -> p.getPartitionType() instanceof PerformanceModel.HardwarePartition)
                            .flatMap(p -> p.getInstances().stream()).collect(Collectors.toSet());
                    solutionToUniqueHwMap.put(new SolutionIdentity(cores, solNumber), hwActors.hashCode());
                    if (!uniqueHardwarePartitions.contains(hwActors)){
                        newSolutions ++;
                    }
                    uniqueHardwarePartitions.add(hwActors);
                }

                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.INFO, "Found new " + newSolutions +
                                " hardware partitions with " + cores + " cores")
                );

            }

            context.getReporter().report(
                    new Diagnostic(
                            Diagnostic.Kind.INFO, "Found total of " + uniqueHardwarePartitions.size() +
                            " unique hardware partitions"
                    )
            );
            // keep the mappings from solutions to unique hardware partitions.
            try {
                String jsonFileName = context.getConfiguration().get(Compiler.targetPath).resolve("hardware.json")
                        .toAbsolutePath().toString();
                dumpUniqueHardwarePartitionJson(solutionToUniqueHwMap, jsonFileName);
            } catch (IOException e) {
                context.getReporter().report(new Diagnostic(
                        Diagnostic.Kind.ERROR, "Could not save the unique hardware partition mappings"
                ));
            }

            // create the unique xcf files
            Map<Connection, Integer> bufferDepth = task.getNetwork().getConnections().stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            c -> this.multicoreDB.getConnectionSettingsDataBase().get(c).getDepth()
                    ));
            File uniqueHardwareDir = new File(
                    context.getConfiguration().get(Compiler.targetPath).resolve("unique").toUri());
            uniqueHardwareDir.mkdirs();

            ImmutableList<Integer> hashCodes = solutionToUniqueHwMap.values().stream().sorted()
                    .collect(ImmutableList.collector());

            // dump unique xcf files
            for (Set<String> hardwareActorsNames : uniqueHardwarePartitions) {

                int hashCode = hardwareActorsNames.hashCode();
                int hashIndex = hashCodes.indexOf(hashCode);

                ImmutableList<Instance> hardwareActors = task.getNetwork().getInstances().stream()
                        .filter(p -> hardwareActorsNames.contains(p.getInstanceName()))
                        .collect(ImmutableList.collector());
                ImmutableList<Instance> softwareActors = task.getNetwork().getInstances().stream()
                        .filter(p -> !hardwareActors.contains(p))
                        .collect(ImmutableList.collector());

                PerformanceModel.Partition<Instance> hwPartition = new PerformanceModel.Partition<>(
                        hardwareActors, new PerformanceModel.HardwarePartition(1));
                PerformanceModel.Partition<Instance> swPartition = new PerformanceModel.Partition<>(
                        softwareActors, new PerformanceModel.SoftwarePartition(0));
                ImmutableList.Builder<PerformanceModel.Partition<Instance>> partitions = ImmutableList.builder();
                partitions.add(swPartition);
                partitions.add(hwPartition);
                PerformanceModel.PartitioningSolution<Instance> sol =
                        new PerformanceModel.PartitioningSolution<>(partitions.build());

                String xcfName = uniqueHardwareDir.toPath()
                        .resolve(hashIndex + "_" + hashCode + ".xcf").toAbsolutePath().toString();
                PerformanceModel.dumpXcfConfig(xcfName, sol,bufferDepth, task);
            }


        }


        return task;
    }


    private void dumpUniqueHardwarePartitionJson(Map<SolutionIdentity, Integer> solutionToUniqueHwMap, String fileName)
            throws IOException {
        // dump a mapping from solution ids to unique hardware sets (as hash codes)
        JsonArray jArray = new JsonArray();
        /**
         * The solution mappings are serialized in a json as follows:
         * {
         *    solutions: [
         *      {
         *          "cores": CORE_COUNT,
         *          "index": SOLUTION_INDEX,
         *          "hash" : HASH_CODE
         *      },...
         *    ]
         * }
         */
        ImmutableList<Integer> hashCodes = solutionToUniqueHwMap.values().stream().sorted()
                .collect(ImmutableList.collector());

        for (SolutionIdentity sol : solutionToUniqueHwMap.keySet()) {
            int hashCode = solutionToUniqueHwMap.get(sol);
            int hashIndex = hashCodes.indexOf(hashCode);
            JsonObject jElem = new JsonObject();
            jElem.addProperty("cores", sol.numberOfCores);
            jElem.addProperty("index", sol.solutionNumber);
            jElem.addProperty("hash", hashCode);
            jElem.addProperty("hash_index", hashIndex);
            jArray.add(jElem);

        }
        JsonObject jObject = new JsonObject();
        jObject.addProperty("count", jArray.size());
        jObject.add("solutions", jArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        FileWriter writer = new FileWriter(fileName);
        gson.toJson(jObject, writer);
        writer.flush();
        context.getReporter().report(new Diagnostic(
                Diagnostic.Kind.INFO, "Saved the unique hardware mappings to " + fileName
        ));


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

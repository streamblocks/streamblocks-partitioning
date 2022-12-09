package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.models.HeterogeneousModel;
import ch.epfl.vlsc.analysis.partitioning.models.MulticoreOrToolsModel;
import ch.epfl.vlsc.analysis.partitioning.models.MulticorePerformanceModel;
import ch.epfl.vlsc.analysis.partitioning.models.PerformanceModel;

import ch.epfl.vlsc.analysis.partitioning.parser.*;
import ch.epfl.vlsc.analysis.partitioning.util.JsonConfiguration;
import ch.epfl.vlsc.analysis.partitioning.util.PartitionSettings;

import ch.epfl.vlsc.analysis.partitioning.util.ProfileData;
import ch.epfl.vlsc.analysis.partitioning.util.SolutionIdentity;
import com.google.gson.*;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;

import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import se.lth.cs.tycho.settings.Setting;
import se.lth.cs.tycho.compiler.Compiler;

import java.io.File;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  public PartitioningAnalysisPhase() {
    this.multicoreDB = null;
    this.multicoreClockPeriod = 0.0;
    this.accelDB = null;
    this.accelClockPeriod = 0.0;

    this.timeLimit = 300.0;
  }

  @Override
  public String getDescription() {
    return "finds a partition based on profiling information";
  }

  @Override
  public List<Setting<?>> getPhaseSettings() {
    return ImmutableList.of(PartitionSettings.jsonConfig);
  }

  private JsonConfiguration parseConfig(CompilationTask task, Context context) {
    if (!context.getConfiguration().isDefined(PartitionSettings.jsonConfig)) {
      throw new CompilationException(
        new Diagnostic(Diagnostic.Kind.ERROR, "config is not defined")
      );

    }
    Path jsonPath = context.getConfiguration().get(PartitionSettings.jsonConfig);
    try {
      Gson gson = new Gson();
      FileReader reader = new FileReader(jsonPath.toFile());
      JsonConfiguration jConfig = gson.fromJson(reader, JsonConfiguration.class);

      reader.close();
      return jConfig;

    } catch (IOException e) {
      context
          .getReporter()
          .report(
              new Diagnostic(
                  Diagnostic.Kind.ERROR,
                  "Could not parse json configuration file " + jsonPath + ":\n" + e.getMessage()));
    }
    return null;
  }

  private void checkProfileData(String name, ProfileData pdata) {

    if (pdata.multiplier == null) {
      context
          .getReporter()
          .report(
              new Diagnostic(
                  Diagnostic.Kind.WARNING,
                  "In " + name + " multiplier is undefined, setting it to 1.0"));
      pdata.multiplier = 1.0;
    }
    if (pdata.freq == null) {
      context
          .getReporter()
          .report(new Diagnostic(Diagnostic.Kind.ERROR, "In  " + name + " freq is not defined!"));
    }
    if (pdata.path == null) {
      context
          .getReporter()
          .report(new Diagnostic(Diagnostic.Kind.ERROR, "In  " + name + " path is not defined!"));
    } else {
      File profileFile = new File(pdata.path);
      if (!profileFile.exists()) {
        context
            .getReporter()
            .report(
                new Diagnostic(
                    Diagnostic.Kind.ERROR,
                    "In "
                        + name
                        + " file "
                        + profileFile.toPath().toAbsolutePath()
                        + " does not exist"));
      }
    }
  }

  private void setConfig(JsonConfiguration jConfig) {

    context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO, "Checking JSON config"));

    if (jConfig.mode == null) {
      context.getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, "mode is undefined!"));
    }
    if (jConfig.cores == 0) {
      context.getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, "cores is undefined!"));
    }
    if (jConfig.name == null) {
      context
          .getReporter()
          .report(new Diagnostic(Diagnostic.Kind.WARNING, "Configuration is not named!"));
    }
    switch (jConfig.mode) {

      case HETEROGENEOUS:
        if (jConfig.systemc == null) {
          context
              .getReporter()
              .report(new Diagnostic(Diagnostic.Kind.ERROR, "systemc is undefined!"));
        }
        checkProfileData("systemc", jConfig.systemc);
        this.accelClockPeriod = 1 / jConfig.systemc.freq * 1e-6;

        if (jConfig.opencl == null) {
          context
              .getReporter()
              .report(new Diagnostic(Diagnostic.Kind.ERROR, "opencl is undefined!"));
        }

        checkProfileData("opencl", jConfig.opencl);

        DeviceProfileParser devParser = new DeviceProfileParser(task, context);
        devParser.parseExecutionProfile(Paths.get(jConfig.systemc.path));
        devParser.parseBandwidthProfile(Paths.get(jConfig.opencl.path));

        this.accelDB = devParser.getDataBase();
      case SIMPLE:
      case HOMOGENEOUS:
        if (jConfig.bandwidth == null) {
          context
              .getReporter()
              .report(new Diagnostic(Diagnostic.Kind.ERROR, "bandwidth is undefined!"));
        }

        checkProfileData("bandwidth", jConfig.bandwidth);

        if (jConfig.software == null) {
          context
              .getReporter()
              .report(new Diagnostic(Diagnostic.Kind.ERROR, "software is undefined!"));
        }

        checkProfileData("software", jConfig.software);
        this.multicoreClockPeriod = 1 / jConfig.software.freq * 1e-6;

        MulticoreProfileParser multicoreParser = new MulticoreProfileParser(task, context, this.multicoreClockPeriod);
        multicoreParser.parseExecutionProfile(Paths.get(jConfig.software.path));
        multicoreParser.parseBandwidthProfile(Paths.get(jConfig.bandwidth.path));
        this.multicoreDB = multicoreParser.getDataBase();

        break;
      case PINNED_HETEROGENEOUS:
        context
            .getReporter()
            .report(
                new Diagnostic(
                    Diagnostic.Kind.ERROR, "pinned_heterogeneous mode is not implemented yet"));
        break;
    }
  }

  @Override
  public CompilationTask execute(CompilationTask task, Context context)
      throws CompilationException {

    this.context = context;
    this.task = task;

    JsonConfiguration jConfig = parseConfig(task, context);
    setConfig(jConfig);

    int maxCores = jConfig.cores;

    printStatistics();

    PartitionSettings.Mode mode = jConfig.mode;
    if (mode == PartitionSettings.Mode.SIMPLE) {
      Path logPath = context.getConfiguration().get(Compiler.targetPath).resolve("simple");
      context
          .getReporter()
          .report(new Diagnostic(Diagnostic.Kind.INFO, "SIMPLE PARTITIONING MODE"));
      MulticoreOrToolsModel perfModel = new MulticoreOrToolsModel(task, context,
          multicoreDB, multicoreClockPeriod, 10 * 60.);
      maxCores = Math.min(task.getNetwork().getInstances().size(), maxCores);

      for (int cores = 2; cores <= maxCores; cores++) {
        ImmutableList<PerformanceModel.PartitioningSolution<String>> solutions = perfModel.solveModel(cores);
        File configDir = logPath.resolve(String.valueOf(cores)).toFile();
        if (!configDir.exists()) {
          configDir.mkdirs();
        }
        // System.out.println("Solved the model for " + cores + " cores");
        for (PerformanceModel.PartitioningSolution<String> solution : solutions) {
          perfModel.dumpMulticoreConfig(
              configDir + "/config_" + solutions.indexOf(solution) + ".xml", solution, multicoreDB);
        }
      }

    } else if (mode == PartitionSettings.Mode.HOMOGENEOUS) {
      Path logPath = context.getConfiguration().get(Compiler.targetPath).resolve("homogeneous");
      context
          .getReporter()
          .report(new Diagnostic(Diagnostic.Kind.INFO, "HOMOGENEOUS PARTITIONING MODE"));

      MulticorePerformanceModel perfModel = new MulticorePerformanceModel(task, context, multicoreDB,
          multicoreClockPeriod, 300.0);

      maxCores = Math.min(perfModel.getMaxPartitions(), maxCores);

      for (int cores = 2; cores <= maxCores; cores++) {
        ImmutableList<PerformanceModel.PartitioningSolution<String>> solutions = perfModel.solveModel(cores);
        File configDir = logPath.resolve(String.valueOf(cores)).toFile();
        if (!configDir.exists()) {
          configDir.mkdirs();
        }
        System.out.println("Solved the model for " + cores + " cores");
        perfModel.solutionsSummary(configDir);
        for (PerformanceModel.PartitioningSolution<String> solution : solutions) {

          perfModel.dumpMulticoreConfig(
              configDir + "/config_" + solutions.indexOf(solution) + ".xml", solution, multicoreDB);
        }
      }

    } else if (mode == PartitionSettings.Mode.HETEROGENEOUS) {
      context
          .getReporter()
          .report(new Diagnostic(Diagnostic.Kind.INFO, "HETEROGENEOUS PARTITIONING MODE"));
      HeterogeneousModel perfModel = new HeterogeneousModel(
          task, context, multicoreDB, accelDB, multicoreClockPeriod, accelClockPeriod, 300.0);
      int solutionCount = 0;
      Map<SolutionIdentity, Integer> solutionToUniqueHwMap = new TreeMap<>(
          (t1, t2) -> {
            if (t1.numberOfCores < t2.numberOfCores) {
              return -1;
            } else if (t1.numberOfCores > t2.numberOfCores) {
              return 1;
            } else {
              return t1.solutionNumber - t2.solutionNumber;
            }
          });
      List<Set<String>> uniqueHardwarePartitions = new ArrayList<>();
      for (int cores = 1; cores <= maxCores; cores++) {

        ImmutableList<PerformanceModel.PartitioningSolution<String>> solutions = perfModel.solveModel(cores);

        // Find the hardware partition for every solution and add the hardware partition
        // to the
        // set of unique hardware partitions and update a map from solution ids to
        // unique hw
        // partitions
        // sets
        int newSolutions = 0;
        for (PerformanceModel.PartitioningSolution<String> sol : solutions) {
          int solNumber = solutions.indexOf(sol);
          Set<String> hwActors = sol.getPartitions().stream()
              .filter(p -> p.getPartitionType() instanceof PerformanceModel.HardwarePartition)
              .flatMap(p -> p.getInstances().stream())
              .collect(Collectors.toSet());
          solutionToUniqueHwMap.put(new SolutionIdentity(cores, solNumber), hwActors.hashCode());
          int hashIndex = -2;
          if (!uniqueHardwarePartitions.contains(hwActors)) {

            uniqueHardwarePartitions.add(hwActors);
            hashIndex = solutionCount;
            newSolutions++;
            solutionCount++;

          } else {
            hashIndex = uniqueHardwarePartitions.indexOf(hwActors);

          }
          solutionToUniqueHwMap.put(new SolutionIdentity(cores, solNumber), hashIndex);

        }

        context
            .getReporter()
            .report(
                new Diagnostic(
                    Diagnostic.Kind.INFO,
                    "Found new " + newSolutions + " hardware partitions with " + cores + " cores"));
      }

      context
          .getReporter()
          .report(
              new Diagnostic(
                  Diagnostic.Kind.INFO,
                  "Found total of "
                      + uniqueHardwarePartitions.size()
                      + " unique hardware partitions"));
      // keep the mappings from solutions to unique hardware partitions.
      try {
        String jsonFileName = context
            .getConfiguration()
            .get(Compiler.targetPath)
            .resolve("heterogeneous/hardware.json")
            .toAbsolutePath()
            .toString();
        dumpUniqueHardwarePartitionJson(solutionToUniqueHwMap, jsonFileName);
      } catch (IOException e) {
        context
            .getReporter()
            .report(
                new Diagnostic(
                    Diagnostic.Kind.ERROR,
                    "Could not save the unique hardware partition mappings"));
      }

      // create the unique xcf files
      Map<Connection, Integer> bufferDepth = task.getNetwork().getConnections().stream()
          .collect(
              Collectors.toMap(
                  Function.identity(),
                  c -> this.multicoreDB.getConnectionSettingsDataBase().get(c).getDepth()));
      File uniqueHardwareDir = new File(context.getConfiguration().get(Compiler.targetPath).resolve("unique").toUri());
      uniqueHardwareDir.mkdirs();

      ImmutableList<Integer> hashCodes = solutionToUniqueHwMap.values().stream().sorted()
          .collect(ImmutableList.collector());

      // dump unique xcf files
      for (Set<String> hardwareActorsNames : uniqueHardwarePartitions) {

        int hashIndex = uniqueHardwarePartitions.indexOf(hardwareActorsNames);

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
        PerformanceModel.PartitioningSolution<Instance> sol = new PerformanceModel.PartitioningSolution<>(
            partitions.build());

        String xcfName = uniqueHardwareDir
            .toPath()
            .resolve("unique_" + hashIndex + ".xcf")
            .toAbsolutePath()
            .toString();
        PerformanceModel.dumpXcfConfig(xcfName, sol, bufferDepth, task);
      }
    }

    return task;
  }

  private void dumpUniqueHardwarePartitionJson(
      Map<SolutionIdentity, Integer> solutionToUniqueHwMap, String fileName) throws IOException {
    // dump a mapping from solution ids to unique hardware sets (as hash codes)
    JsonArray jArray = new JsonArray();
    /**
     * The solution mappings are serialized in a json as follows: { solutions: [ {
     * "cores":
     * CORE_COUNT, "index": SOLUTION_INDEX, "hash" : HASH_CODE },... ] }
     */
    ImmutableList<Integer> hashCodes = solutionToUniqueHwMap.values().stream().sorted()
        .collect(ImmutableList.collector());

    for (SolutionIdentity sol : solutionToUniqueHwMap.keySet()) {

      int hashIndex = solutionToUniqueHwMap.get(sol);
      JsonObject jElem = new JsonObject();
      jElem.addProperty("cores", sol.numberOfCores);
      jElem.addProperty("index", sol.solutionNumber);
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
    context
        .getReporter()
        .report(
            new Diagnostic(
                Diagnostic.Kind.INFO, "Saved the unique hardware mappings to " + fileName));
  }

  private void printStatistics() {

    LinkedHashMap<Instance, Long> softwareInstances = this.multicoreDB.getExecutionProfileDataBase().entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

    System.out.println("Software actor stats:");
    System.out.println("Actor execution stats:\t\tSoftware\t\tHardware (ms)");

    for (Instance instance : this.task.getNetwork().getInstances()) {
      Double sw = this.multicoreDB.getInstanceTicks(instance) * this.multicoreClockPeriod * 1e3;
      Double hw = Double.valueOf(0.0);
      if (this.accelDB != null) {
        hw = this.accelDB.getInstanceTicks(instance) * this.accelClockPeriod * 1e3;
      }
      System.out.printf("%20s:\t\t%06.4f\t\t%06.4f\t\t(ms)\n", instance.getInstanceName(), sw, hw);
    }
  }
}

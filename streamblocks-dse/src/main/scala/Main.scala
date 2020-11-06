import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.LinkedBlockingQueue

import hypermapper.{DesignPoint, HMSymmetricPartitionParam, HyperMapperConfig, HyperMapperProcess, NetworkRun, ProgramRunner, RequestHandler, ResponseHandler}
import model.{Actor, Configuration, Network}
import utils.{CLIConfig, Config, RuntimeConfig}

import scala.xml.{Elem, NodeSeq, XML}
import scopt.OptionParser



object CLIConfig {

  private lazy val parser: OptionParser[CLIConfig] = new OptionParser[CLIConfig]("streamblocks-dse"){

    def validateFile(x: File): Either[String, Unit] = {
      if (x.exists())
        success
      else
        failure(s"file ${x.toPath.toAbsolutePath} does not exist")
    }

    def estimationOptions = {
      opt[File]("multicore-profile").action{(x, c) =>
        c.copy(multicoreProfilePath = x)
      }.text("multicore profile xml file")
        .validate(validateFile)
      opt[File]("system-profile").action{ (x,c) =>
        c.copy(systemProfilePath = x)
      }.text("multicore communication bandwidth profile xml")
        .validate{validateFile}
      opt[File]("systemc-profile").optional.action{ (x, c) =>
        c.copy(systemcProfilePath = x)
      }.text("systemc (hardware) profile xml file")
      opt[File]("ocl-profile").optional.action{ (x, c) =>
        c.copy(oclProfilePath = x)
      }.text("OpenCL bandwidth profile xml")
        .validate(validateFile)
      opt[Double]("accelerator-freq").action{ (x, c) => c.copy(acceleratorClock = 1000.0 / x)}
        .text("Accelerator operating frequency in MHz, e.g., 327.5")
        .validate(x => if (x > 0.0) success else failure("invalid accelerator frequency"))
      opt[Double]("cpu-freq").action{ (x, c) => c.copy(multicoreClock = 1000.0 / x) }
        .text("CPU clock frequency in MHz, e.g., 2400.5")
        .validate(x => if (x > 0.0) success else failure("invalid cpu frequency"))
    }

    def executionOptions = {
      opt[File]("executable").action{ (x, c) =>
        c.copy(binaryPath = x)
      }.text("program binary file to be executed")
        .validate(x =>
          if (x.exists()) success
          else failure(s"binary program ${x.toPath.toAbsolutePath} does not exist"))
      opt[File]("work-directory").action{ (x, c) =>
        c.copy(workDir = x)
      }
    }
    head("streamblocks-dse", "0.1")
    note("streamblocks design space exploration using HyperMapper")


    opt[File]("xcf-path").required.action{ (x, c) =>
      c.copy(xcfPath = x)
    }.text("the xcf configuration file.")
      .validate(validateFile)

    opt[File]("output-path").required.action{ (x, c) =>
      c.copy(outputPath = x)
    }.text("output path for HyperMapper logs and the the explored partitions")

    opt[Int]('m', "min-cores").action{ (x, c) => c.copy(minNumberOfCores = x)}
      .text("minimum number of cores to explore")
      .validate{x =>
        if (x >= 2) success
        else failure("minimum number of cores should be more than 2")
      }
    opt[Int]('M', "max-cores").required.action{ (x, c) => c.copy(maxNumberOfCores = x)}
      .text("maximum number of cores to explore")

    opt[Unit]("symmetric-analysis").action{ (x, c) => c.copy(symmetricAnalysis = true)}
      .text("Assume the cores are symmetric to reduce the design space size")

    opt[Map[String, String]]("hypermapper-params")
      .valueName("number_of_trees=N,optimization_iterations=M,number_of_samples=K")
      .action { (x, c) =>
        c.copy(hyperMapperSettings = x)
      }.text("HyperMapper parameters")
      .validate{x =>
        val validParams = Set("number_of_trees", "optimization_iterations", "number_of_samples")
        if (x.keySet subsetOf validParams)
          success
        else
          failure(s"valid HyperMapper params are ${validParams.mkString(",")}")
      }

    help("print this message and exit")
    // Estimator mode
    cmd("estimator").action{ (_, c) =>
      c.copy(runMode = utils.RunMode.ESTIMATION)
    }.text("Use static performance estimation to drive the space exploration")
      .children{
        estimationOptions
      }

    // Executor mode
    cmd("executor").action{ (_, c) =>
      c.copy(runMode = utils.RunMode.EXECUTION)
    }.text("use real program execution to drive the space exploration")
      .children{
        executionOptions
      }

    // hybrid
    cmd("hybrid").action{ (_, c) =>
      c.copy(runMode = utils.RunMode.HYBRID)
    }.text("use real program execution to drive the search but also keep estimations")
      .children{
        estimationOptions
        executionOptions
      }

  }



  def parse(args: Array[String]): CLIConfig = parser.parse(args, new CLIConfig()) match {
    case Some(config) => config
    case None =>
      System.err.println("Could not parse command line arguments, use --help or -h for help.")
      sys.exit(-1)
  }
}

object Main {
  def main(args:Array[String]): Unit = {

    val configuration = CLIConfig.parse(args)
    val xcfFile = configuration.xcfPath
    val baseNetwork = Network.fromXcf(xcfFile)
    val dseTable = if (configuration.symmetricAnalysis) {
      println("Generating Stirling DSE table...")
      Option(utils.StirlingTable(baseNetwork.actors.length))
    } else {
      None
    }
    for (cores <- configuration.minNumberOfCores to
      Seq(configuration.maxNumberOfCores, baseNetwork.actors.length).min) {

      val network =  baseNetwork withCores cores

      val dseSize = dseTable.get(network.actors.length, cores)

      val jsonConfig = HyperMapperConfig(
        appName = network.name + "_" + cores + (if (configuration.symmetricAnalysis) "_sym" else ""),
        cliConfig = configuration,
        dseParams =
          if (configuration.symmetricAnalysis)
            HMSymmetricPartitionParam.chunked(utils.Constants.PARTITION_INDEX, dseSize)
          else
            network.actors.map(_.partition)
      )

      val runtimeConfig = RuntimeConfig(
        originalNetwork = baseNetwork,
        network = network,
        stirlingTable = dseTable,
        hmConfig = jsonConfig,
        numCores = cores,
        cliConfig = configuration
      )

      println(s"Starting optimization with ${network.actors.length} actors " +
        s"and ${cores} cores (symmetric = ${configuration.symmetricAnalysis}, dse size = ${dseSize})")

      val thisOutputDirectory = new File(configuration.outputPath.toPath.toAbsolutePath.toString + "/" + cores)
      if (!thisOutputDirectory.exists()) {
        thisOutputDirectory.mkdirs()
      }

      val jsonFile = jsonConfig.emitJson(thisOutputDirectory)

      val hmHome = sys.env.getOrElse("HYPERMAPPER_HOME", throw new RuntimeException("HYPERMAPPER_HOME env variable not set."))

      val hmProcess = HyperMapperProcess(hmHome, configuration.outputPath, jsonFile.toFile, 2048)
      val (reader, writer, error) = hmProcess.run
      val requestQueue = new LinkedBlockingQueue[Seq[Network]](5000)
      val responseQueue = new LinkedBlockingQueue[Seq[DesignPoint]](5000)
      val keysQueue = new LinkedBlockingQueue[(Int, Seq[String])](5000)
      val doneQueue = new LinkedBlockingQueue[Boolean](20)
      val optimalQueue = new LinkedBlockingQueue[List[NetworkRun]](configuration.savedSolutions + 1)
      val reqHandler = new Thread(
        RequestHandler(
          input = reader,
          output = requestQueue,
          keysOut = keysQueue,
          config = runtimeConfig)
      )
      val program = new Thread(
        ProgramRunner(
          workDir = configuration.workDir,
          binary = configuration.binaryPath,
          input = requestQueue,
          output = responseQueue,
          optimalNetwork = optimalQueue,
          config = runtimeConfig)
      )
      val respHandler = new Thread(
        ResponseHandler(
          output = writer,
          input = responseQueue,
          keysIn = keysQueue,
          doneQueue = doneQueue)
      )

      reqHandler.start()
      program.start()
      respHandler.start()

      doneQueue.take()
      val solutions: List[NetworkRun] = optimalQueue.take()
      println(s"Optimisation finished with ${solutions.length} solutions!")
      var ix = 0
      val infoFile = new File(thisOutputDirectory + "/runs.csv")
      val infoWriter = new PrintWriter(infoFile)
      infoWriter.println(s"execution_time,estimated_time")
      solutions.foreach { run =>
        val n = run.network
        model.Configuration.write(
          configDir = thisOutputDirectory,
          network = baseNetwork withName (n.name + "_optimal_" + ix) withActors n.actors
        )
        val exec = run.executed
        val est = run.estimated
        infoWriter.println(s"${exec.objective.getObjective},${est.objective.getObjective}")
        ix += 1
      }
      infoWriter.close()

    }

  }
}
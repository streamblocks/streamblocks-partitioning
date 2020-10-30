import java.io.File
import java.nio.file.{Files, Paths}
import java.util.concurrent.LinkedBlockingQueue

import hypermapper.{DesignPoint, HMSymmetricPartitionParam, HyperMapperConfig, HyperMapperProcess, ProgramRunner, RequestHandler, ResponseHandler}
import model.{Actor, Configuration, Network}
import utils.Config

import scala.xml.{Elem, NodeSeq, XML}


object CLIOptions {

  val ConfigFile = Symbol("config-file")
  val NumCores = Symbol("num-cores")
  val OutputDir = Symbol("output-dir")
  val Binary =  Symbol("binary")
  val WorkDir = Symbol("work-dir")
  val Symmetric = Symbol("symmetric")
  type OptionMap = Map[Symbol, String]

  def parseNext(options: OptionMap, args: List[String]): OptionMap = {

    args match {
      case Nil => options
      case "--config-file" :: value :: tail =>
        parseNext(options ++ Map(ConfigFile -> value), tail)
      case "--num-cores" :: value :: tail =>
        parseNext(options ++ Map(NumCores -> value), tail)
      case "--output-dir" :: value :: tail =>
        parseNext(options ++ Map(OutputDir -> value), tail)
      case "--binary" :: value :: tail =>
        parseNext(options ++ Map(Binary -> value), tail)
      case "--work-dir" :: value :: tail =>
        parseNext(options ++ Map(WorkDir -> value), tail)
      case "--symmetric" :: tail =>
        parseNext(options ++ Map(Symmetric -> "true"), tail)
      case option::tail =>
        println("Unknown option " + option)
        sys.exit(1)
    }

  }
  def parseArgs(args: Array[String]): OptionMap = parseNext(Map(), args.toList)
}
object Main {
  def main(args:Array[String]): Unit = {

    val usage =
      s"""
         |Usage: streamblocks_dse --config-file CONFIG_FILE  --num-cores NUM_CORES --output-dir DIRECTORY --binary BINARY --work-dir WORK_DIR --symmetric
         |""".stripMargin


    if (args.length == 0) {
      println(usage)
      sys.exit(1)
    } else {


      val options = CLIOptions.parseArgs(args)
      val configFileName: String = options.getOrElse(CLIOptions.ConfigFile,
        throw new RuntimeException("Config file name not specified"))
      val numCores = options.getOrElse(CLIOptions.NumCores,
        throw new RuntimeException("Number of cores not specified")).toInt
      val outputDir = options.getOrElse(CLIOptions.OutputDir,
        throw new RuntimeException("Output directory not specified"))
      val binary = options.getOrElse(CLIOptions.Binary,
        throw new RuntimeException("Binary not specified"))
      val workDir = options.getOrElse(CLIOptions.WorkDir,
        throw new RuntimeException("Work directory not specified"))


      val symmetricAnalysis = if (options.getOrElse(CLIOptions.Symmetric, "false") == "false") false else true

      val configFile = new File(configFileName)

      val outputPath = Paths.get(outputDir).toAbsolutePath
      if (!Files.exists(outputPath))
        Files.createDirectory(outputPath)
      val binaryProgram = new File(binary)
      if (!binaryProgram.exists)
        throw new RuntimeException("Binary program does not exist")
      val workPath = Paths.get(workDir)
      if (!Files.exists(workPath))
        Files.createDirectory(workPath)



      val baseNetwork = Network.fromXcf(configFile)


      val dseTable = if (symmetricAnalysis) {
        println("Generating Stirling DSE table...")
        Option(utils.StirlingTable(baseNetwork.actors.length))
      } else {
        Option.empty
      }



      for (cores <- 2 to Seq(numCores, baseNetwork.actors.length).min) {



        val network =  baseNetwork withCores cores


        val dseSize = dseTable.get(network.actors.length, cores)

        val jsonConfig = HyperMapperConfig(
            appName = network.name + "_" + cores + (if (symmetricAnalysis) "_sym" else ""),
            numIter = utils.Constants.HMIterations,
            doe = utils.Constants.HMDOE,
            dseParams =
            if (symmetricAnalysis)
                HMSymmetricPartitionParam.chunked(utils.Constants.PARTITION_INDEX, dseSize)
            else
                network.actors.map(_.partition)
        )

        val config = Config(
          networkFile = configFile,
          numCores = cores,
          outputDir = outputPath.toFile,
          programBinary = binaryProgram,
          workDir = workPath.toFile,
          network = network,
          symmetric = symmetricAnalysis,
          stirlingTable = dseTable,
          jsonConf = jsonConfig
        )
        println(s"Starting optimization with ${network.actors.length} actors ${cores} and cores (symmetric = ${symmetricAnalysis}, dse size = ${dseSize})")

        val jsonFile = jsonConfig.emitJson(config.outputDir)


        val hmHome = sys.env.getOrElse("HYPERMAPPER_HOME", throw new RuntimeException("HYPERMAPPER_HOME env variable not set."))

        val hmProcess = HyperMapperProcess(hmHome, outputPath.toFile, jsonFile.toFile, 2048)
        val (reader, writer, error) = hmProcess.run

        val requestQueue = new LinkedBlockingQueue[Seq[Network]](5000)
        val responseQueue = new LinkedBlockingQueue[Seq[DesignPoint]](5000)
        val keysQueue = new LinkedBlockingQueue[(Int, Seq[String])](5000)
        val doneQueue = new LinkedBlockingQueue[Boolean](20)
        val optimalQueue = new LinkedBlockingQueue[Network](20)
        val reqHandler = new Thread(
          RequestHandler(
            input = reader,
            output = requestQueue,
            keysOut = keysQueue,
            config = config)
        )
        val program = new Thread(
          ProgramRunner(
            workDir = workPath.toFile,
            binary = binaryProgram,
            input = requestQueue,
            output = responseQueue,
            optimalNetwork = optimalQueue)
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
        println("Optimisation finished!")
        val optimalPoint = optimalQueue.take()
        println("Saving optimal results")

        model.Configuration.write(
          configDir = config.outputDir,
          network = baseNetwork withName (optimalPoint.name + "_" + cores + "_optimal") withActors optimalPoint.actors
        )
      }

    }

  }
}
import java.io.File
import java.nio.file.{Files, Paths}

import hypermapper.HMSymmetricPartitionParam
import model.{Actor, Configuration, Network}

import scala.util.Random
import scala.xml.XML

object Generator {


  object CLIOptions {
    val XcfFile = Symbol("xcf")
    val NumCores = Symbol("num-cores")
    val Accelerator = Symbol("accel")
    val OutputXcf = Symbol("output")
    type OptionMap = Map[Symbol, String]

    private def parseNext(options: OptionMap, args: List[String]): OptionMap = {

      args match {
        case Nil => options
        case "--xcf" :: value :: tail =>
          parseNext(options ++ Map(XcfFile -> value), tail)
        case "--num-cores" :: value :: tail =>
          parseNext(options ++ Map(NumCores -> value), tail)
        case "--accel" :: value :: tail =>
          parseNext(options ++ Map(Accelerator -> value), tail)
        case "--output" :: value :: tail =>
          parseNext(options ++ Map(OutputXcf -> value), tail)
        case option::tail =>
          println("Unknown option " + option)
          sys.exit(1)
      }

    }
    def parseArgs(args: Array[String]): OptionMap = parseNext(Map(), args.toList)
  }



  def main(args: Array[String]): Unit = {

    val usage =
      s"""
         |Usage:partition_generator --num-core NUM_CORES --xcf XCF_FILE --output OUTPUT_PATH --accel ACCELERATED_COUNT
         |""".stripMargin

    if (args.length == 0) {
      println(usage)
      sys.exit(1)
    }

    val options = CLIOptions.parseArgs(args)
    val xcfPath: String = options.getOrElse(CLIOptions.XcfFile,
      throw new RuntimeException("xcf file not specified")
    )
    val numCores: Int = options.getOrElse(CLIOptions.NumCores,
      throw new RuntimeException("Number of cores not specified")
    ).toInt
    val accelerated: Int = options.getOrElse(CLIOptions.Accelerator, "0").toInt


    val outputPath = Paths
      .get(options.getOrElse(CLIOptions.OutputXcf, throw new RuntimeException("output file not specified")))
      .toAbsolutePath

    val outputFile = new File(outputPath.toString)


    val xcfFile = XML.loadFile(xcfPath)


    println("Parsing xcf file")
    val networkName = (xcfFile \ "network").map(_.attribute("id")).head
      .getOrElse(throw new RuntimeException("Could not get the network name")).toString
    val partitions: Map[String, Seq[String]] = (xcfFile \\ "partition").map{
      n =>
        val partition = n.attribute("id").getOrElse(throw new RuntimeException("partition id not specified")).toString
        val instances: Seq[String] = if (partition == "hw" || partition == "sw") {
          (n \ "instance").map(_.attribute("id")
            .getOrElse(throw new RuntimeException("encountered instance with no id")).toString)
        } else {
          throw new RuntimeException("Invalid partition id " + partition)
        }
        partition -> instances
    }.toMap

    // Shuffle instances
    val movableInstances: Seq[String] = partitions("hw")
    val pinnedInstances: Seq[String] = partitions("sw")

    if (accelerated > movableInstances.length) {
      println(s"Can not accelerate ${accelerated} actors while only ${movableInstances.length} actors are movable")
      sys.exit(1)
    }


    val shuffledInstances: Seq[String] = Random.shuffle(movableInstances)
    // Select a random set of instances for CPU execution
    val softwareInstances = pinnedInstances ++ shuffledInstances.slice(0,
      shuffledInstances.length - accelerated)
    val hardwareInstances = shuffledInstances.slice(
      shuffledInstances.length - accelerated, shuffledInstances.length)

    val dseTable = utils.StirlingTable(softwareInstances.length)

    if (softwareInstances.length < numCores) {
      println(s"Cannot place ${softwareInstances.length} actors on ${numCores} cores!")
      sys.exit(1)
    }
    val dseSize = dseTable(softwareInstances.length, numCores)
    println("The design space size is " + dseSize)


    val partitionParams = HMSymmetricPartitionParam.chunked(
      name = utils.Constants.PARTITION_INDEX,
      size = dseSize,
      random = true)

    def flattenedGrowthSequenceIndex(indexSeq: Seq[HMSymmetricPartitionParam]): BigInt = indexSeq match {
      case Seq() => BigInt(0)
      case sq@_ =>
        sq.head.value + sq.head.size * flattenedGrowthSequenceIndex(sq.tail)
    }

    val spaceIndex = flattenedGrowthSequenceIndex(partitionParams)

    val growthSequence = dseTable.growthSequence(numCores, spaceIndex)

    val softwareActors = (softwareInstances zip growthSequence) map {
      case (inst, affinity) =>
        Actor(inst, affinity, numCores)
    }

    val hardwareActors = hardwareInstances map {
      inst => Actor(inst, numCores, numCores + 1)
    }

    val network = Network(
      name = networkName,
      actors = softwareActors ++ hardwareActors
    )

    Configuration.write(outputFile, network)
  }


}

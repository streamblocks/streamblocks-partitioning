package hypermapper

import java.io.BufferedReader
import java.util.concurrent.BlockingQueue

import model.{Actor, Network}
import utils.{Config, RuntimeConfig}

import scala.util.Try
import scala.util.matching.Regex

case class RequestHandler(input: BufferedReader,
                          output: BlockingQueue[Seq[Network]],
                          keysOut: BlockingQueue[(Int, Seq[String])],
                          config: RuntimeConfig) extends Runnable {
  private var running: Boolean = true

  def flattenedGrowthSequenceIndex(indexSeq: Seq[HMSymmetricPartitionParam]): BigInt = indexSeq match {
    case Seq() => BigInt(0)
    case sq@_ =>
      sq.head.value + sq.head.size * flattenedGrowthSequenceIndex(sq.tail)
  }
  private def createNetworks(requestCount: Int): Unit = {
    val keys = input.readLine().split(",").map(_.trim)
    keysOut.put((requestCount, keys))
    try {
      def generateNetworks(ix: Int): Seq[Network] = {
        if (ix == 0)
          Seq()
        else {
          val values = input.readLine().split(",").map(_.trim)
          val network = if (config.cliConfig.symmetricAnalysis) {
            val definedParams = config.hmConfig.dseParams
            assert(values.length == definedParams.length, "json config params and request param mismatch!")
            val recvParams = (keys zip values) map {
              case (name, value) =>
                val size = definedParams.find(p => p.toString == name).getOrElse(
                  throw new RuntimeException(s"could not find parameter ${name}"))
                  .asInstanceOf[HMSymmetricPartitionParam].size
                HMSymmetricPartitionParam(name, BigInt(value), size)
            } sortBy (p => p.name.substring(utils.Constants.PARTITION_INDEX.length + 1).toInt)
            val growthSequenceIndex: BigInt = flattenedGrowthSequenceIndex(recvParams)
            val growthSequence = config.stirlingTable.get.growthSequence(config.numCores, growthSequenceIndex)
            Network(
              name = config.network.name,
              actors = (config.network.actors zip growthSequence) map {
                case(actor, affinity) =>
                  Actor(actor.name, affinity, config.numCores)
              },
              connections = Seq(),
              index = Some(recvParams)
            )

          } else {
            val typedValues: Seq[Int] = values.map(v => v.toInt)
            config.network withActors {
              (keys zip typedValues) map {
                case (name, affinity) =>
                  Actor(name, affinity, config.numCores)
              }
            }

          }
          network +: generateNetworks(ix - 1)
        }
      }
      output.put(generateNetworks(requestCount))
    } catch {
      case  t =>
        println("Error creating networks")
        t.printStackTrace()
    }
  }

  object Request {
    lazy val regex: Regex = raw"Request ([0-9]*)".r

    def unapply(x: String): Option[Int] = x match {
      case regex(n) => Try(n.toInt).toOption
      case _ => None
    }
  }
  object FRequest {
    lazy val regex: Regex = raw"FRequest ([0-9]*) (.*)".r

    def unapply(x: String): Option[(Int, String)] = x match {
      case regex(n, file) => Try(n.toInt).toOption.map{i => (i, file)}
      case _ => None
    }
  }

  override def run(): Unit = {
    try {
      println("Request handler started..")
      while (running) {
        val requestHeader = input.readLine()
        if (requestHeader == null)
          running = false
        else {
          println("Received request: " + requestHeader)
          requestHeader match {
            case Request(n)  =>
              createNetworks(n)
            case FRequest(n, file) => throw new RuntimeException("FRequest not supported!")
            case "End"                => running = false
            case "Pareto"             => running = false
            case "End of HyperMapper" => running = false
            case msg@_ => throw new RuntimeException("Unsupported request " + msg)
          }
        }
      }
      output.put(Seq())
      keysOut.put((0, Seq()))
      input.close()
    } catch {
      case t =>
        println("Encountered error while handling request.")
        t.printStackTrace()
    }
  }
}

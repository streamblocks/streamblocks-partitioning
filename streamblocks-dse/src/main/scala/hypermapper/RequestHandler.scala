package hypermapper

import java.io.BufferedReader
import java.util.concurrent.BlockingQueue

import model.{Actor, Network}
import utils.Config

import scala.util.Try
import scala.util.matching.Regex

case class RequestHandler(input: BufferedReader,
                          output: BlockingQueue[Seq[Network]],
                          keysOut: BlockingQueue[(Int, Seq[String])],
                          config: Config) extends Runnable {
  private var running: Boolean = true

  def createNetworks(requestCount: Int) = {
    val keys = input.readLine().split(",").map(_.trim)
    keysOut.put((requestCount, keys))
    try {
      def generateNetworks(ix: Int): Seq[Network] = {
        if (ix == 0)
          Seq()
        else {
          val values = input.readLine().split(",").map(_.trim.toInt)
          val network = Network(
            name = config.network.name,
            actors = (keys zip values) map {case (k, v) => Actor(k, v, config.numCores)}
          )
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

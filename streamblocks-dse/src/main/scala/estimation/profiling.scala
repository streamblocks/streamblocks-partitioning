package estimation

import java.io.File
import java.nio.file.{Files, Path}

import model.{Actor, Connection, Network}

import scala.xml.XML



case class BandwidthInfo(intraCore: Long, interCore: Long)
case class ConnectionInfo(tokens: Long, tokenSize: Int, bufferSize: Int)

class CommonProfileDataBase(val profilePath: Path, val systemProfilePath: Path, val network: Network) {


  if (!Files.exists(profilePath)) {
    throw new RuntimeException(s"file ${profilePath.toAbsolutePath} does not exist")
  }

  val xmlFile = XML.loadFile(profilePath.toFile)

  val networkName = (xmlFile \ "network").map(_.attribute("id")).head

  private val execDb: Map[Actor, Long] = (xmlFile \\ "instance").map {
    node =>
      val actorName = node.attribute("id")
        .getOrElse(throw new RuntimeException("actor id not specified")).toString
      val actor: Actor =
        network.actors.find(_.name == actorName).getOrElse(s"Could not find actor ${actorName} in the network")
      val ticks = node.attribute("complexity")
        .getOrElse(throw new RuntimeException("actor ticks (complexity) not specified")).toString.toLong
      actor-> ticks

  }.toMap

  private val commDb: Map[Connection, ConnectionInfo] = (xmlFile \\ "connection").map {
    node =>
      val srcPort = node.attribute("src-port")
        .getOrElse(throw new RuntimeException("Connection source port not specified")).toString
      val srcActor = node.attribute("src")
        .getOrElse(throw new RuntimeException("Connection source actor not specified")).toString
      val dstPort = node.attribute("dst-port")
        .getOrElse(throw new RuntimeException("Connection destination port not specified")).toString
      val dstActor = node.attribute("dst")
        .getOrElse(throw new RuntimeException("Connection source actor not specified")).toString

      val c = Connection(srcActor, srcPort, dstActor, dstPort)

      val connection: Connection =
        network.connections.find(_ == c).getOrElse(
          throw new RuntimeException(s"Invalid connection ${c}")
        )

      val tokens = node.attribute("bandwidth")
        .getOrElse(throw new RuntimeException(s"Connection ${c} bandwidth not specified")).toString.toLong
      val tokenSz = node.attribute("token-size")
        .getOrElse(throw new RuntimeException(s"Connection ${c} token size not specified")).toString.toInt
      val buffSz = node.attribute("size")
          .getOrElse(throw new RuntimeException(s"Connection ${c} connection size not specified")).toString.toInt

      connection -> ConnectionInfo(tokens, tokenSz, buffSz)

  }.toMap

  if (!Files.exists(systemProfilePath)) {
    throw new RuntimeException(s"file ${systemProfilePath.toAbsolutePath} does not exist")
  }

  val systemXmlFile = XML.loadFile(systemProfilePath.toFile)

  def systemDbParser: String => Map[Long, Long] = testType => {
    (systemXmlFile \\ "bandwidth-test").filter { test =>
      test.attribute("type").getOrElse(throw new RuntimeException("bandwidth test type not specified"))
        .toString == testType
    }.flatMap {
      test =>
        val data: Seq[(Long, Long)] = (test \\ "Connection").map {c =>
          val ticks = c.attribute("ticks").getOrElse(throw new RuntimeException("connection ticks not specified"))
            .toString.toLong
          val buffSz = c.attribute("buffer-size")
            .getOrElse(throw new RuntimeException("connection buffer size not specified")).toString.toLong
          buffSz -> ticks
        }
        data
    }.toMap
  }

  private val systemDbSingle = systemDbParser("single")
  private val systemDbMulti = systemDbParser("multi")

  if (systemDbSingle.keySet != systemDbMulti.keySet) {
    throw new RuntimeException("Single a multi core bandwidth profiles do not match")
  }

  private val systemDb = systemDbSingle.map { case (k: Long, v: Long) =>
    k -> BandwidthInfo(v, systemDbMulti(k))
  }




  def apply(actor: Actor) = execDb(actor)
  def apply(con : Connection)  = {
    val (tokens, bufferSize, tokenSize) = commDb(con) match {case ConnectionInfo(t, b, s) => (t, b, s)}

    val bufferBytes = bufferSize * tokenSize
    // The measured buffer sizes are power 2 in bytes, so we need to find the closest measured buffer size in bytes
    val closestMeasuredBufferBytes =
      if (Integer.highestOneBit(bufferBytes) == bufferBytes)
        bufferBytes
      else
        Integer.highestOneBit(bufferBytes) << 1
    val ticksPerTransfer = systemDb.getOrElse(closestMeasuredBufferBytes,
      throw new RuntimeException(s"Buffer size ${closestMeasuredBufferBytes} is not measured!"))
    val numTransfers: Long = (tokens - 1) / closestMeasuredBufferBytes + 1
    (numTransfers * ticksPerTransfer.intraCore, numTransfers * ticksPerTransfer.interCore)
  }


}
object profiling {

}

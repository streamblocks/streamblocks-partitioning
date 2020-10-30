package model

import java.io.File

import hypermapper.HMSymmetricPartitionParam

import scala.xml.XML

case class Network(name: String,
                   actors: Seq[Actor],
                   connections: Seq[Connection],
                   index: Option[Seq[HMSymmetricPartitionParam]] = None) {
  def withCores(numCores: Int): Network =
    Network(name, actors.map(actor => Actor(actor.name, 0, numCores)), connections, index)
  def withActors(thatActors: Seq[Actor]): Network =
    Network(name, thatActors, connections, index)
  def withName(thatName: String): Network =
    Network(thatName, actors, connections, index)
}

object Network {

  def fromXcf(xcfFile: File): Network = {

    if (!xcfFile.exists()) {
      throw new RuntimeException(s"Could not open file ${xcfFile.toPath.toAbsolutePath.toString}")
    }
    val xcf = XML.loadFile(xcfFile)

    val networkNetwork = (xcf \ "network").map(_.attribute("id")
      .getOrElse("could not parse network name!").toString).head
    val instances = (xcf \\ "instance").map(_.attribute("id")
      .getOrElse(throw new RuntimeException("encountered instance with not id!")).toString)

    val connections = (xcf \\ "fifo-connection").map { n =>
      val sourceActor = n.attribute("source").getOrElse("could not get source actor").toString
      val sourcePort = n.attribute("source-port").getOrElse("could not get source port").toString
      val targetActor = n.attribute("target").getOrElse("could not get target actor").toString
      val targetPort = n.attribute("target-port").getOrElse("could not get target port").toString
      Connection(srcActor = sourceActor, srcPort = sourcePort, dstActor = targetActor, dstPort = targetPort)
    }

    // TODO check that the connections are valid!

    val actors = instances.map(Actor(_))

    Network(
      name = networkNetwork,
      actors = actors,
      connections = connections
    )
  }

}
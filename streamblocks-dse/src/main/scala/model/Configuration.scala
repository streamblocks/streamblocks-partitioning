package model;

import java.io.File
import java.nio.file.{Files, Path, Paths}

import scala.xml.XML
import scala.xml.PrettyPrinter
object Configuration {

  def write(configDir: File, network: Network): Path = {

    val nonEmptyPartitions: Seq[String] = network.actors.map(_.partition.getValue).toSet.toSeq.sorted

    val partitions = nonEmptyPartitions.map(p =>
      (p, network.actors.filter(_.partition.getValue == p))
    )

    def xmlInstances(actors: Seq[Actor]) = actors.map(actor => <instance id={actor.name}/>)
    def xmlPartition(partition: (String, Seq[Actor])) = {
      val partId = partition._1
      val actors = partition._2
      <partition id={partId.toString}>{xmlInstances(actors)}</partition>
    }
    val config =
      <configuration>
        <partitioning>{partitions.map(xmlPartition)}</partitioning>
      </configuration>

    val p = new PrettyPrinter(80, 4)
    val configsFormatted = XML.loadString(p.format(config))
    val xmlPath = configDir + "/" + network.name + ".xml"
    XML.save(xmlPath, configsFormatted)
    Paths.get(xmlPath)
  }
}
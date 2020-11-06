package estimation

import java.io.File
import java.nio.file.{Files, Path}

import model.{Actor, Connection, Network}

import scala.xml.XML

case class AcceleratorProfile(profilePath: File, systemProfilePath: File, network: Network) {

  if (!profilePath.exists()) {
    throw new RuntimeException(s"file ${profilePath.toPath.toAbsolutePath} does not exist")
  }

  private val systemCXml = XML.loadFile(profilePath)

  // a map from actors to the number of ticks required in hardware. A tick is a single FPGA clock cycle (e.g. 3.3ns)
  private val execDb: Map[Actor, Long] = (systemCXml \\ "actor").map {
    node =>
      val actorName: String = node.attribute("id")
        .getOrElse(throw new RuntimeException("actor id not specified")).toString
      val totalCycles: Long = node.attribute("clockcycles-total")
        .getOrElse(throw new RuntimeException(s"total clock cycles not specified for actor ${actorName}"))
        .toString.toLong
      val actor: Actor = network.actors.filter(a => a.name == actorName).head
      actor -> totalCycles
  }.toMap


  case class OpenCLTicks(writeTicks: Long, readTicks: Long, kernelTicks: Long, readSizeTicks: Long, repeats: Long)

  if (!systemProfilePath.exists()) {
    throw new RuntimeException(s"file ${systemProfilePath.toPath.toAbsolutePath} does not exist")
  }
  // A map from buffer size to opencl communication ticks in nanoseconds
  private val oclDb: Map[Long, OpenCLTicks] = {
    val xmlFile = XML.loadFile(systemProfilePath)
    (xmlFile \\ "Connection").map { node =>
      val kernelBw =
        node.attribute("kernel-total").getOrElse(throw new RuntimeException("Could not get kernel bw")).toString.toLong
      val writeBw =
        node.attribute("write-total").getOrElse(throw new RuntimeException("Could not get the write bw"))
          .toString.toLong
      val readBw = node.attribute("read-total")
            .getOrElse(throw new RuntimeException("Could not get the read bw")).toString.toLong
      val bufferSize = node.attribute("buffer-size")
        .getOrElse(throw new RuntimeException("could not get the buffer size")).toString.toLong
      val readSizeBw = node.attribute("read-size-total").getOrElse(throw new RuntimeException("Could not get " +
        "read size bw")).toString.toLong
      val repeats = node.attribute("repeats").getOrElse(throw new RuntimeException("Could not get repeat count"))
        .toString.toLong
      bufferSize -> OpenCLTicks(writeBw, readBw, kernelBw, readSizeBw, repeats)
    }.toMap

  }

  def apply(actor: Actor): Long = execDb(actor)
  private def getReadWriteTime(bufferSizeBytes: Long): OpenCLTicks =  {

    val closestMeasuredTransfer: Long =
      if (java.lang.Long.highestOneBit(bufferSizeBytes) == bufferSizeBytes)
        bufferSizeBytes
      else
        java.lang.Long.highestOneBit(bufferSizeBytes.toInt) << 1

    val ticks = oclDb.getOrElse(closestMeasuredTransfer,
      throw new RuntimeException(s"buffer size ${closestMeasuredTransfer} is measured"))

    ticks
  }

  /**
   * Computes PCIe read time for a given buffer size in bytes and the number of
   * bytes exchanged over that buffer
   * @param bufferSize the size of the buffer in bytes
   * @param bytesExchanged total number of bytes transferred over the buffer
   * @return
   */
  def readTime(bufferSize: Long, bytesExchanged: Long): Double = {

    val (rticks, rsticks, repeats) = getReadWriteTime(bufferSize ) match {
      case OpenCLTicks(_, r, _, rs, reps) => (r, rs, reps)
    }
    val averageTime = (rsticks + rticks).doubleValue() / repeats.doubleValue()
    val numTransfers = bytesExchanged.doubleValue() / bufferSize.doubleValue()

    averageTime * numTransfers

  }

  /**
   * Computes the PCIe write time for a given buffer size in bytes and the number of bytes
   * exchanged over that buffer
   * @param bufferSize size of the buffer in bytes
   * @param bytesExchanged number of bytes transferred over the buffer
   * @return
   */
  def writeTime(bufferSize: Long, bytesExchanged: Long): Double = {
    val (wticks, repeats) = getReadWriteTime(bufferSize) match {case OpenCLTicks(w, _, _, _, reps) => (w, reps) }
    val averageTime = wticks.doubleValue() / repeats.doubleValue()
    val numTransfers = bytesExchanged.doubleValue() / bufferSize.doubleValue();
    averageTime * numTransfers
  }




}

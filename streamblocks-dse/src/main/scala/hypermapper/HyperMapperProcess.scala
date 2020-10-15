package hypermapper

import java.io.{BufferedReader, BufferedWriter, File, InputStream, InputStreamReader, OutputStream, OutputStreamWriter, PipedInputStream, PipedOutputStream}

import scala.sys.process
import scala.sys.process.ProcessIO


case class HyperMapperProcess(hmHome: String, workDir: File, jsonConfig: File, pipeSize: Int) {


  private var hmProcess: process.ProcessBuilder =
    process.Process(
      command = Seq("python3", hmHome + "/scripts/hypermapper.py", jsonConfig.toString),
      cwd = workDir,
      extraEnv = ("PYTHONPATH", hmHome + "/scripts"))

  private var reader: java.io.BufferedReader = _
  private var writer: java.io.BufferedWriter = _
  private var error: java.io.BufferedReader = _
  /**
   * pipeIStream is the piped input stream of HM, which is actually the output stream of its producer
   */
  private val pipeIStream = new PipedOutputStream()
  /**
   * pipeOStream is the piped output stream of HM, which is the input stream of its consumer
   */
  private val pipeOStream = new PipedInputStream()

  private val pipeErrStream = new PipedInputStream()

  /**
   * Method to attach to HyperMapper's input stream
   * @param hmIn
   */
  private def inputWriter(hmIn: OutputStream) = {
    // create an input stream
    val istream = new java.io.PipedInputStream(pipeIStream)
    val bytes = Array.fill[Byte](pipeSize)(0.toByte)
    var bytesRead = 0
    while(bytesRead >= 0) {
      bytesRead = istream.read(bytes)
      if (bytesRead > 0) {
        hmIn.write(bytes, 0, bytesRead)
        hmIn.flush()
      }
    }
    hmIn.close()
  }



  private def outputReader(hmOut: InputStream) = {
    //create an output stream
    val ostream = new PipedOutputStream(pipeOStream)
    val bytes = Array.fill[Byte](pipeSize)(0.toByte)
    var bytesRead = 0
    while (bytesRead >=0) {
      println("Waiting on HyperMapper request")
      bytesRead = hmOut.read(bytes)
      if (bytesRead > 0) {
        println("Receiving HyperMapper Request")
        ostream.write(bytes, 0, bytesRead)

      }
    }
    hmOut.close()
  }
  private def errorReader(err: InputStream) = {

    val errstream = new java.io.PipedOutputStream(pipeErrStream)
    val bytes = Array.fill[Byte](pipeSize)(0.toByte)
    var bytesRead = 0
    while(bytesRead >= 0) {
      bytesRead = err.read(bytes)
      if (bytesRead > 0) {

        errstream.write(bytes, 0, bytesRead)
        println(bytes.map(_.toChar).mkString)
      }
    }

  }
  def run(): (BufferedReader, BufferedWriter, BufferedReader) = {


    reader = new BufferedReader(new InputStreamReader(pipeOStream))
    writer = new BufferedWriter(new OutputStreamWriter(pipeIStream))
    error = new BufferedReader(new InputStreamReader(pipeErrStream))
    // TODO: take care of stderr
    val hmIO = new ProcessIO(inputWriter, outputReader, errorReader) //stderr is discarded
    // Execute the process (in background)
    println("Executing " + hmProcess)
    hmProcess run hmIO
    (reader, writer, error)
  }

  def close() = {
    pipeErrStream.close()
    pipeIStream.close()
    pipeOStream.close()
  }
}

object HyperMapperProcess {
  def apply(hmHome: String, workDir: File, jsonConfig: File, pipeSize: Int) =
    new HyperMapperProcess(hmHome, workDir, jsonConfig, pipeSize)
}



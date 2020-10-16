package hypermapper

import java.io.BufferedWriter
import java.util.concurrent.BlockingQueue

import model.Network

case class ResponseHandler(output: BufferedWriter,
                           input: BlockingQueue[Seq[DesignPoint]],
                           keysIn: BlockingQueue[(Int, Seq[String])],
                           doneQueue: BlockingQueue[Boolean]) extends Runnable {
  private var running = true
  override def run(): Unit = {

    try {
      println("Starting response handler")
      while(running) {
        val (n, k) = keysIn.take
        if (n == 0)
          running = false
        else {
          val keys = (k ++ Seq("exec_time", "num_cores", "Valid")).mkString(",")
          // Write the header
          output.write(keys + "\n")
          println("Response keys are: " + keys)

          val evaluatedPoints: Seq[DesignPoint] = input.take()

          val respStrings: Seq[String] = evaluatedPoints.map {
            case DesignPoint(params, obj) =>
              params.map(_.getValue).mkString(",") + "," + (obj match {
                case InfeasibleObjective() =>
                  Seq("0.0", "0.0", utils.Constants.HMFalse).mkString(",")
                case FeasibleObjective(execTime, util) =>
                  Seq(execTime.toString, util.toString, utils.Constants.HMTrue).mkString(",")
              })
          }
          respStrings foreach { pts =>
            println(s"Sending response $pts")
            output.write(pts + "\n")
          }
          output.flush()

        }
      }
      doneQueue.put(true)
      output.close()
    } catch {
      case t =>
        println("Error in response handler")
        t.printStackTrace()
    }
  }
}
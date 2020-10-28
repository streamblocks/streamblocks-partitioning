package hypermapper

import java.io.File
import java.util.concurrent.BlockingQueue

import model.Network

import scala.sys.process
import scala.sys.process.ProcessLogger

case class ProgramStats(exitCode: Int, time: Double, stdout: String, stderr: String)
case class ProgramRunner(workDir: File, binary: File,
                         input: BlockingQueue[Seq[Network]],
                         output: BlockingQueue[Seq[DesignPoint]],
                         optimalNetwork: BlockingQueue[Network]) extends Runnable {

  private var running = true
  private var optimalFound: (Network, Double) = (Network("null_network", Seq()), Double.MaxValue)

  def execute(network: Network): ProgramStats = {

    // create a config file based on the partition
    val configFile = model.Configuration.write(
      configDir =  workDir,
      network = network
    )

    val cmd = Seq(binary.toPath.toAbsolutePath.toString, "--cfile=" + configFile)
    println(s"Executing ${cmd.mkString(" " )}" )
    val program = process.Process(
      command = Seq(binary.toPath.toAbsolutePath.toString, "--cfile=" + configFile),
      cwd = workDir
    )
    val programOutput = (new StringBuilder, new StringBuilder)
    val startTime = System.nanoTime()
    val exitCode = program ! ProcessLogger(programOutput._1 append _ , programOutput._2 append _)
    val endTime = System.nanoTime()
    val duration = (endTime - startTime) * 1e-9
    println(s"Finished in ${duration} s with exitcode $exitCode" + (if (exitCode != 0) " (failed) " else ""))
    ProgramStats(exitCode, duration, programOutput._1.toString, programOutput._2.toString())
  }

//  def getUtil(network: Network): Int = network.actors.map(_.partition.affinity).max + 1
  def getUtil(network: Network) = 100
  def getDesignPoint(network: Network, time: Double) = DesignPoint(network, time)
  def getDesignPoint(network: Network) = DesignPoint(network)
  override def run(): Unit = {
    try {

      while(running) {
        val networks = input.take()
        networks match {
          case Seq() => running = false
          case _ =>
            val objectives = networks.map(n => {
              val state = execute(n)
              if (state.exitCode == 0)
                (n, getDesignPoint(n, state.time))
              else
                (n, getDesignPoint(n))
            })

            val feasibles = objectives.filter(o => o._2.objective match {
              case InfeasibleObjective() => false
              case FeasibleObjective(_) => true
            })
            if (feasibles.length > 0) {
              val bestPoint = feasibles.minBy(p => p._2.objective.getObjective)
              optimalFound = if (bestPoint._2.objective.getObjective < optimalFound._2)
                (bestPoint._1, bestPoint._2.objective.getObjective) else optimalFound
            }

            val results: Seq[DesignPoint] = objectives.map(_._2)
            output.put(results)
        }
      }
      optimalNetwork.put(optimalFound._1)
    } catch {
      case t =>
        println("Encountered error in executing the program")
        t.printStackTrace()
    }
  }


}

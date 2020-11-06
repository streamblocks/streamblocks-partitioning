package hypermapper

import java.io.File
import java.util.concurrent.BlockingQueue

import estimation.{PerformanceEstimator, ProfileDB}
import model.Network
import utils.RuntimeConfig


import scala.sys.process
import scala.sys.process.ProcessLogger

case class NetworkRun(network: Network, executed: DesignPoint, estimated: DesignPoint)

case class ProgramStats(exitCode: Int, time: Double, stdout: String, stderr: String)

case class ProgramRunner(workDir: File, binary: File,
                         input: BlockingQueue[Seq[Network]],
                         output: BlockingQueue[Seq[DesignPoint]],
                         optimalNetwork: BlockingQueue[List[NetworkRun]],
                         config: RuntimeConfig) extends Runnable {

  private var running = true

  private var optimalList: List[NetworkRun] = List()

  private val profileDb =
    if (shouldEstimate)
      Some(ProfileDB(
        network = config.originalNetwork,
        multicoreProfilePath = config.cliConfig.multicoreProfilePath,
        systemProfilePath = config.cliConfig.systemProfilePath,
        multicoreClock = config.cliConfig.multicoreClock
      ))
    else
      None
  private val perfModel =
    if (shouldEstimate)
      Some(
        PerformanceEstimator(
          profDb = profileDb.get,
          network = config.originalNetwork,
          numCores = config.numCores
        )
      )
    else
      None

  private def shouldEstimate: Boolean =
    (config.cliConfig.runMode == utils.RunMode.ESTIMATION) || (config.cliConfig.runMode == utils.RunMode.HYBRID)

  private def useExecution: Boolean =
    (config.cliConfig.runMode == utils.RunMode.EXECUTION) || (config.cliConfig.runMode == utils.RunMode.HYBRID)

  def execute(network: Network): ProgramStats = {

    if (!useExecution) {
      // dry run
      ProgramStats(0, 0.0, "", "")
    } else {
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

  }

  def estimate(network: Network): ProgramStats = {
    if (shouldEstimate) {
      val model = perfModel.get
      val estimatedTime = model estimateByComponent network
      estimatedTime foreach { case (comp, time) =>
        print(f"${comp}= ${time * 1e-9}%3.3f\t")
      }

      val totalTime = estimatedTime map {case (_, time) => time} reduce (_+_)
      ProgramStats(0, totalTime * 1e-9, "", "")
    } else {
      ProgramStats(0, 0.0, "", "")
    }

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
            val networkRuns: List[NetworkRun] = networks.map(n => {
              val executedState = execute(n)
              val estimatedState = estimate(n)

              val state = if (useExecution) executedState else estimatedState

              if (state.exitCode == 0)
                NetworkRun(n, getDesignPoint(n, executedState.time), getDesignPoint(n, estimatedState.time))
              else
                NetworkRun(n, getDesignPoint(n), getDesignPoint(n, estimatedState.time))
            }).toList

            val feasibles = networkRuns.filter(r => r.executed.objective match {
              case InfeasibleObjective => false
              case FeasibleObjective(_) => true
            })
            if (feasibles.nonEmpty) {
              val mixedSolutions = optimalList ++ feasibles
              val newBest = mixedSolutions.sortBy{
                r =>
                  if (useExecution)
                    r.executed.objective
                else
                    r.estimated.objective
              }{
                (a, b) => a.getObjective compare b.getObjective
              } take {
                if (config.cliConfig.savedSolutions < mixedSolutions.size)
                  config.cliConfig.savedSolutions
                else
                  mixedSolutions.size
              }
              optimalList = newBest
            }

            val results: Seq[DesignPoint] = {
              if (useExecution)
                networkRuns.map(_.executed)
              else
                networkRuns.map(_.estimated)
            }
            output.put(results)
        }
      }
      println(s"emitting ${optimalList.size} networks:")
      optimalNetwork.put(optimalList)


    } catch {
      case t =>
        println("Encountered error in executing the program")
        t.printStackTrace()
    }
  }


}

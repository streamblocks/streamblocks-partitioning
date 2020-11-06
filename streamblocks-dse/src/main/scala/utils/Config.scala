package utils

import java.io.File

import hypermapper.HyperMapperConfig
import model.Network
import utils.RunMode

object RunMode extends Enumeration {
  type RunMode = Value

  val NONE = Value("none")
  val ESTIMATION = Value("estimation")
  val EXECUTION = Value("execution")
  val HYBRID = Value("hybrid")
}

case class Config(networkFile: File, numCores: Int,
                  outputDir: File, programBinary: File,
                  workDir: File, network: Network,
                  symmetric: Boolean,
                  stirlingTable: Option[utils.StirlingTable],
                  jsonConf: HyperMapperConfig)




case class CLIConfig(runMode: RunMode.RunMode = RunMode.NONE,
                     xcfPath: File = new File("."),
                     outputPath: File = new File("."),
                     workDir: File = new File("."),
                     symmetricAnalysis: Boolean = false,
                     binaryPath: File = new File("."),
                     maxNumberOfCores: Int = 4,
                     minNumberOfCores: Int = 2,
                     multicoreProfilePath: File = new File("."),
                     systemProfilePath: File = new File("."),
                     systemcProfilePath: File = new File("."),
                     oclProfilePath: File = new File("."),
                     multicoreClock: Double = 0.0,
                     acceleratorClock: Double = 0.0,
                     hyperMapperSettings: Map[String, String] =
                     Map(
                       "number_of_trees" -> 300.toString,
                       "number_of_samples" -> 50.toString,
                       "optimization_iterations" -> 200.toString
                     ),
                     savedSolutions: Int = 200
                    )

case class RuntimeConfig(originalNetwork: Network,
                         network: Network,
                         numCores: Int,
                         stirlingTable: Option[utils.StirlingTable],
                         hmConfig: HyperMapperConfig, cliConfig: CLIConfig)

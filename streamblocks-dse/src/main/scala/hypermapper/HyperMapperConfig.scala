package hypermapper

import java.io.{File, PrintWriter}
import java.nio.file.Paths

import utils.CLIConfig

case class HyperMapperConfig(appName : String, cliConfig: CLIConfig,
                             dseParams: Seq[HMParam]) {

  def emitJson(outputDir: File) = {
    val jsonFile: String = outputDir + "/" + appName + ".json"
    println("Creating HyperMapper config JSON file")

    val filePrinter = new PrintWriter(new File(jsonFile))

    filePrinter.println("{")
    filePrinter.print(
      s"""
         |  "application_name": "${appName}",
         |  "design_of_experiment" : {
         |    "doe_type": "standard latin hypercube",
         |    "number_of_samples": ${cliConfig.hyperMapperSettings("number_of_samples")}
         |  },
         |  "feasible_output" : {
         |    "enable_feasible_predictor": true,
         |    "false_value": "${utils.Constants.HMFalse}",
         |    "true_value" : "${utils.Constants.HMTrue}"
         |  },
         |  "hypermapper_mode": {
         |    "mode": "client-server"
         |  },
         |  "input_parameters": {
         |""".stripMargin)
    dseParams.foreach(p =>
      filePrinter.print(
        s"""
           |    "$p": {
           |      "parameter_type": "${p.getType}",
           |      "values": ${p.getType.getRange}
           |    }${if (dseParams.indexOf(p) == dseParams.length - 1) "" else ","}
           |""".stripMargin)
    )
    filePrinter.print(
      s"""
         |  },
         |  "log_file": "${outputDir}/${appName}.log",
         |  "models": {
         |    "model": "random_forest",
         |    "number_of_trees": ${cliConfig.hyperMapperSettings("number_of_trees")}
         |  },
         |  "optimization_iterations": ${cliConfig.hyperMapperSettings("optimization_iterations")},
         |  "optimization_objectives": [
         |    "exec_time"
         |  ],
         |  "optimization_method": "bayesian_optimization",
         |
         |  "output_data_file": "$outputDir/$appName.csv"
         |""".stripMargin)
    filePrinter.print("}")

    filePrinter.flush()
    Paths.get(jsonFile).toAbsolutePath
  }
}


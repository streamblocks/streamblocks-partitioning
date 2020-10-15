package hypermapper

import java.io.{File, PrintWriter}
import java.nio.file.Paths

case class HyperMapperConfig(appName : String, numInter: Integer, doe: Integer,
                             dseParams: Seq[HMPartitionParam]) {

  def emitJson(outputDir: File) = {
    val jsonFile: String = outputDir + "/" + appName + ".json"
    println("Creating HyperMapper config JSON file")

    val filePrinter = new PrintWriter(new File(jsonFile))

    filePrinter.println("{")
    filePrinter.print(
      s"""
         |  "application_name": "${appName}",
         |  "design_of_experiment" : {
         |    "doe_type": "random sampling",
         |    "number_of_samples": ${doe}
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
           |      "values": [${p.getType.toInt}, ${p.getType.maxValue}]
           |    }${if (dseParams.indexOf(p) == dseParams.length - 1) "" else ","}
           |""".stripMargin)
    )
    filePrinter.print(
      s"""
         |  },
         |  "log_file": "${outputDir}/${appName}.log",
         |  "models": {
         |    "model": "random_forest"
         |  },
         |  "optimization_iterations": ${numInter},
         |  "optimization_objectives": [
         |    "exec_time",
         |    "num_cores"
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


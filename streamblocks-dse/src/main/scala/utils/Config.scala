package utils

import java.io.File

import model.Network

case class Config(networkFile: File, numCores: Int,
                  outputDir: File, programBinary: File,
                  workDir: File, network: Network,
                  symmetric: Boolean,
                  stirlingTable: Option[utils.StirlingTable])

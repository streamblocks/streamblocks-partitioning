package model

import java.io.File

import hypermapper.{HMAsymmetricPartitionParam, HMEmptyParam, HMParam, HMSymmetricPartitionParam}





case class Actor(name: String, partition: HMParam)



object Actor {

  def apply(name: String) = new Actor(name, HMEmptyParam)
  def apply(name: String, partition: HMParam) = new Actor(name, partition)
  def apply(name: String, affinity: Int, numCores: Int) = new Actor(name, HMAsymmetricPartitionParam(name, affinity, numCores))


}


package model

import hypermapper.{HMAsymmetricPartitionParam, HMParam, HMSymmetricPartitionParam}



case class Network(name: String, actors: Seq[Actor], index: Option[HMSymmetricPartitionParam] = None)

case class Actor(name: String, partition: HMParam)



object Actor {

  def apply(name: String, partition: HMParam) = new Actor(name, partition)
  def apply(name: String, affinity: Int, numCores: Int) = new Actor(name, HMAsymmetricPartitionParam(name, affinity, numCores))


}

object Network {

  def apply(name: String, actors: Seq[Actor]) = new Network(name, actors)


}
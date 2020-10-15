package model

import hypermapper.HMPartitionParam



case class Network(name: String, actors: Seq[Actor])

case class Actor(name: String, partition: HMPartitionParam)



object Actor {

  def apply(name: String, partition: HMPartitionParam) = new Actor(name, partition)
  def apply(name: String, affinity: Int, numCores: Int) = new Actor(name, HMPartitionParam(name, affinity, numCores))


}

object Network {

  def apply(name: String, actors: Seq[Actor]) = new Network(name, actors)


}
package model

import hypermapper.{HMAsymmetricPartitionParam, HMParam, HMSymmetricPartitionParam}




case class Network(name: String, actors: Seq[Actor], connections: Seq[Connection],
                   index: Option[Seq[HMSymmetricPartitionParam]] = None)

case class Actor(name: String, partition: HMParam)

case class Connection(srcActor: String, srcPort: String, dstActor: String, dstPort: String,
                      size: Option[Int] = None, capacity: Option[Int] = None) {
  def ==(that: Connection) =
    (this.srcActor == that.srcActor) &&
    (this.srcPort == that.srcPort) &&
    (this.dstActor == that.dstActor) &&
    (this.dstPort == that.dstPort)

  override def toString: String = s"${srcActor}.${srcPort}-->${dstActor}.${dstPort}"
}

object Actor {

  def apply(name: String, partition: HMParam) = new Actor(name, partition)
  def apply(name: String, affinity: Int, numCores: Int) = new Actor(name, HMAsymmetricPartitionParam(name, affinity, numCores))


}

object Network {

  def apply(name: String, actors: Seq[Actor]) = new Network(name, actors, Seq())


}
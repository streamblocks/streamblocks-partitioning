package hypermapper

import model.Network

abstract class DesignObjective {
  def getObjective: Double
}
case class InfeasibleObjective() extends DesignObjective {
  override def getObjective: Double = Double.MaxValue
}
case class FeasibleObjective(execTime: Double, util: Int) extends DesignObjective {
  override def getObjective: Double = execTime
}

object DesignObjective {
  def apply(execTime: Double, util: Int) = new FeasibleObjective(execTime, util)
  def apply() = new InfeasibleObjective()
}

case class DesignPoint(params: Seq[HMPartitionParam], objective: DesignObjective)

object DesignPoint {

  def apply(params: Seq[HMPartitionParam], objective: DesignObjective) =
    new DesignPoint(params, objective)
  def apply(network: Network, execTime: Double, util: Int) =
    new DesignPoint(network.actors.map(_.partition), DesignObjective(execTime, util))
  def apply(network: Network) = new DesignPoint(network.actors.map(_.partition), InfeasibleObjective())
}
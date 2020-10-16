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

case class DesignPoint(params: Seq[HMParam], objective: DesignObjective)

object DesignPoint {


  def apply(network: Network, execTime: Double, util: Int): DesignPoint = network.index match {
    case None =>
      new DesignPoint(network.actors.map(_.partition), DesignObjective(execTime, util))
    case Some(ix) =>
      new DesignPoint(Seq(ix), DesignObjective(execTime, util))
  }
  def apply(network: Network): DesignPoint = network.index match {
    case None =>
      DesignPoint(network.actors.map(_.partition), InfeasibleObjective())
    case Some(ix) =>
      DesignPoint(Seq(ix), InfeasibleObjective())
  }

}
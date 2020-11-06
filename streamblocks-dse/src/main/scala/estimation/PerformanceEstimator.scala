package estimation

import hypermapper.{HMAsymmetricPartitionParam, HMSymmetricPartitionParam, HyperMapperConfig}
import model.{Actor, Network}




case class PerformanceEstimator(profDb: ProfileDB, network: Network, numCores: Int) {


  // The growth variables represent a restricted growth sequence that model the core assignments
  private val growthVariables = network.actors.map(a => a.name -> Variable(a.name)).toMap

  // formulate each core communication time
  private def partitionExecutionTimeFormula(p: Int): Formula = {
    val terms = {

      val decisionVars: Seq[Formula] = network.actors.map{ a =>
        val v = growthVariables(a.name)
        val w: Lit = Lit(Literal(DoubleLiteral(profDb(a)(ProfileDB.ProfileType.SW))))
        val zero: Lit = Lit(Literal(IntegerLiteral(0)))
        val currentPartition = Literal(IntegerLiteral(p))
        If(currentPartition == v, w, zero)
      }
      decisionVars
    }
    // Each partition execution time is the sum of individual actor execution times on that partition,
    // decisionVars computed above take either the value obtained from profiling (if the actor is on
    // the corresponding partition) or they are zero.
    Formula.Sum(terms)
  }



  private def findActor(name: String): Actor =
    network.actors.find(a => a.name == name).getOrElse(throw new Exception(s"Could not find actor ${name}"))


  /**
   * Creates a formula to model intra-core communication for a given partition p
   * @param p a partition
   * @return
   */
  def localCommTimeFormula(p: Int): Formula = Formula.Sum(network.connections.map {c =>



    val sourceActor = findActor(c.srcActor)
    val destActor = findActor(c.dstActor)

    val sourcePartition = growthVariables(sourceActor.name)
    val destPartition = growthVariables(destActor.name)

    val intraCost = profDb(c) match {case (i, _) => Literal(DoubleLiteral(i))}
    val currentPartition = Literal(IntegerLiteral(p))

    val zero = Lit(Literal(DoubleLiteral(0)))

    If(sourcePartition == destPartition,
      If(sourcePartition == currentPartition, Lit(intraCost),zero), zero)

  })

  /**
   * Creates a formula to model inter-core communication cost
   * @param p1 partition of the source
   * @param p2 partition of the destination
   * @return A Formula for the inter-core communication cost that depends on the partition variables
   */
  private def globalCommTimeFormula(p1: Int, p2: Int): Formula =  {
    if (p1 == p2) {
      // In case source and destination partitions are the same
      Lit(Literal(IntegerLiteral(0)))
    } else {

      val sourcePartition = Literal(IntegerLiteral(p1))
      val targetPartition = Literal(IntegerLiteral(p2))

      Formula.Sum(network.connections.map {c =>

        val interCost = profDb(c) match { case (_, i) => Literal(DoubleLiteral(i))}
        val sourceActor = findActor(c.srcActor)
        val targetActor = findActor(c.dstActor)
        val sourceActorPartition = growthVariables(sourceActor.name)
        val targetActorPartition = growthVariables(targetActor.name)
        val zero = Lit(Literal(DoubleLiteral(0)))
        If(sourceActorPartition == sourcePartition,
          If(targetActorPartition == targetPartition,
            Lit(interCost),
            zero),
          zero)

      })
    }
  }


  private val coreList = Range(0, numCores).toList

  private val execTime = Formula.Max(coreList.map(partitionExecutionTimeFormula))
  private val localCommTime =  Formula.Max(coreList.map(localCommTimeFormula))
  private val globalCommTime = Formula.Sum {
    coreList.flatMap(c1 => coreList.map(c2 => (c1, c2))).map {
      case (core1, core2) => globalCommTimeFormula(core1, core2)
    }
  }


  // Total execution time, includes communication time and execution time
  private val totalTime = Formula.Max(Seq(execTime, localCommTime, globalCommTime))

  def estimate(network: Network): Double = {
    estimate(network, totalTime)
  }

  def estimateByComponent(network: Network): Map[String, Double] = {
    val Texec = estimate(network, execTime)
    val Tlc = estimate(network, localCommTime)
    val Tcc = estimate(network, globalCommTime)
    Map(
      "T_exec" -> Texec,
      "T_lc" -> Tlc,
      "T_cc" -> Tcc
    )
  }

  private def estimate(network: Network, formula: Formula): Double = {
    val substFormula = Formula.substitute(formula) {
      // A map from growth variables to their literal values
      network.actors.map(a => a.partition match {
        case HMAsymmetricPartitionParam(_, value, _) =>
          growthVariables(a.name)-> value.toLong
        case HMSymmetricPartitionParam(_, _, _) =>
          throw new RuntimeException("Actor partition param should be converted to an AsymmetricPartitionParam")
      }).toMap
    }
    val foldedFormula: LiteralType = Formula.evaluate(substFormula) match {
      case Lit(l) => l.l
      case _ => throw new RuntimeException("Could not evaluate the formula!")
    }
    foldedFormula match {
      case DoubleLiteral(d) => d
      case IntegerLiteral(i) => i.toDouble
      case BooleanLiteral(b) => throw new RuntimeException("Error evaluating the formula!")
    }
  }

  def reportProfile = {

    println(f"actor\t\t\t\t\t\tsoftware\t\thardware")

    network.actors.foreach{actor =>
      println(f"${actor.name}%20s\t\t\t\t" +
        f"${profDb(actor)(ProfileDB.ProfileType.SW) * 1e-9}%2.6f\t\t${profDb(actor)(ProfileDB.ProfileType.HW) * 1e-9}%2.6f")
    }
    val hwTime = network.actors.map(profDb(_)(ProfileDB.ProfileType.HW)).sum * 1e-9
    val swTime = network.actors.map(profDb(_)(ProfileDB.ProfileType.SW)).sum * 1e-9
    println(f"\t\t\t\t\t\t\t${swTime}%2.6f\t\t${hwTime}%2.6f")



  }

}

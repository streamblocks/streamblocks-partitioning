package estimation

import java.io.File
import java.nio.file.Paths

import model.{Actor, Network}

import scala.util.Random


sealed trait LiteralType{

  def identityValue: LiteralType
  def getValue: AnyVal

  def toDoubleLiteral: DoubleLiteral = this match {
    case IntegerLiteral(x) => DoubleLiteral(x)
    case t@DoubleLiteral(_) => t
    case BooleanLiteral(x) => DoubleLiteral(if (x == true) 1.0 else 0.0)
  }

}

case class IntegerLiteral(value: Long) extends LiteralType {
  override def identityValue: LiteralType = IntegerLiteral(1)

  override def getValue: Long = value
}
case class DoubleLiteral(value: Double) extends LiteralType {
  override def identityValue: LiteralType = DoubleLiteral(1.0)

  override def getValue: Double = value
}
case class BooleanLiteral(value: Boolean) extends LiteralType {
  override def identityValue: LiteralType = BooleanLiteral(true)

  override def getValue: Boolean = value
}



case class Variable(name: String) {

  def + (that: Variable) = Expression(AddOperator, Seq(Var(this), Var(that)))
  def + (that: Literal) = Expression(AddOperator, Seq(Var(this), Lit(that)))
  def - (that: Variable) = Expression(SubOperator, Seq(Var(this), Var(that)))
  def - (that: Literal) = Expression(SubOperator, Seq(Var(this), Lit(that)))

  def ==(that: Variable) = Expression(EqualityOperator, Seq(Var(this), Var(that)))
  def ==(that: Literal) = Expression(EqualityOperator, Seq(Var(this), Lit(that)))

}
case class Literal(l: LiteralType) {

  def + (that: Literal) = Expression(AddOperator, Seq(Lit(this), Lit(that)))
  def + (that: Variable) = Expression(AddOperator, Seq(Lit(this), Var(that)))

  def - (that: Literal) = Expression(SubOperator, Seq(Lit(this), Lit(that)))
  def - (that: Variable) = Expression(SubOperator, Seq(Lit(this), Var(that)))

  def *(that: Literal) = Expression(MultiplyOperator, Seq(Lit(this), Lit(that)))
  def *(that: Variable) = Expression(MultiplyOperator, Seq(Lit(this), Var(that)))

  def ==(that: Variable) = Expression(EqualityOperator, Seq(Lit(this), Var(that)))
  def ==(that: Literal) = Expression(EqualityOperator, Seq(Lit(this), Lit(that)))
}




sealed abstract class Operator(val name: String) {
  override def toString: String = name
}

sealed abstract class ValueOperator(name: String) extends Operator(name)
sealed abstract class TestOperator(name: String) extends Operator(name)

case object AddOperator extends ValueOperator("+")
case object SubOperator extends ValueOperator("-")
case object MaxOperator extends ValueOperator("max")
case object SumOperator extends ValueOperator("sum")
case object MultiplyOperator extends ValueOperator("*")

case object EqualityOperator extends TestOperator("==")

sealed trait Atom extends Product {

  def asVariable: Option[Variable]
  def asLiteral: Option[Literal]
}

case class AtomVariable(v: Variable) extends Atom {
  override def asVariable: Option[Variable] = Some(v)
  override def asLiteral: Option[Literal] = None
}

case class AtomLiteral(l: Literal) extends Atom {
  override def asVariable: Option[Variable] = None
  override def asLiteral: Option[Literal] = Some(l)
}


sealed trait Formula

case class Expression(op: Operator, args: Seq[Formula]) extends Formula {

  def +(that: Expression) = Expression(AddOperator, Seq(this, that))
  def -(that: Expression) = Expression(SubOperator, Seq(this, that))
  def ==(that: Expression) = Expression(EqualityOperator, Seq(this, that))
  def *(that: Expression) = Expression(MultiplyOperator, Seq(this, that))
}
case class Lit(value: Literal) extends Formula
case class Var(variable: Variable) extends Formula
case class If(cond: Formula, thenFormula: Formula, elseFormula: Formula) extends Formula


object Formula {

  def apply(weights: Map[String, Int], network: Network, numCores: Int): Formula = {

    val growthVariables = network.actors.map(a => Variable(a.name))

    val partitionExecutionTime: Seq[Formula] = Range(0, numCores)
      .map(p => Literal(IntegerLiteral(p))).map { p =>
        val deltas = growthVariables.map{ a_i =>
          val w = Lit(Literal(IntegerLiteral(weights(a_i.name))))
          If(p == a_i,
            w,
            Lit(Literal(IntegerLiteral(0))))
        }
        Sum(deltas)
      }
    Max(partitionExecutionTime)
  }

  def Max(args: Seq[Formula]) = Expression(MaxOperator, args)
  def Sum(args: Seq[Formula]) = Expression(SumOperator, args)

  def substitute(formula: Formula)(implicit subst: Map[Variable, Long]): Formula = formula match {
    case Expression(op, args) => Expression(op, args.map(substitute(_)))
    case If(cond, thn, els) => If(substitute(cond), substitute(thn), substitute(els))
    case Var(v) => Lit(Literal(IntegerLiteral(subst(v))))
    case Lit(_) => formula
  }
  def evaluate(formula: Formula): Formula = formula match {
    case Expression(op, args) =>
      val evaluatedArgs = args.map(evaluate)
      val allLiterals = evaluatedArgs.forall(a => a match {
        case Lit(_) => true
        case _ => false
      })
      if (allLiterals) {
        val cArgs = evaluatedArgs.map(_.asInstanceOf[Lit].value.l)
        val homoArgs = makeHomomorphic(cArgs)
        val foldedVal = constantEvaluator(op, cArgs)
        Lit(Literal(foldedVal))
      } else
        formula
    case If(cond, thn, els) =>
      evaluate(cond) match {
        case Lit(l) => l.l match {
          case BooleanLiteral(b) => if (b) evaluate(thn) else evaluate(els)
          case _ => throw new RuntimeException("If condition should be Boolean!")
        }
        case _=> formula // do not evaluate
      }
    case Lit(_) => formula
    case Var(_) => formula
  }


  lazy val constantEvaluator: PartialFunction[(Operator, Seq[LiteralType]), LiteralType] = {
    case (AddOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => IntegerLiteral(x.value  + y.value)
    case (AddOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => DoubleLiteral(x.value + y.value)
    case (SubOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => IntegerLiteral(x.value + y.value)
    case (SubOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => DoubleLiteral(x.value + y.value)
    case (MultiplyOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => IntegerLiteral(x.value + y.value)
    case (MultiplyOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => DoubleLiteral(x.value + y.value)
    case (MaxOperator, xs) =>
      xs.map(_.toDoubleLiteral).maxBy(_.value)
    case (SumOperator, xs) =>
      DoubleLiteral(xs.map(_.toDoubleLiteral.value).sum)
    case (EqualityOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => BooleanLiteral(x.value == y.value)
    case (EqualityOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => BooleanLiteral(x.value == y.value)
    case (EqualityOperator, Seq(x:BooleanLiteral, y: BooleanLiteral)) => BooleanLiteral(x.value == y.value)
    case _ => throw new RuntimeException("Unsupported constants evaluation")
  }

  private def makeHomomorphic(values: Seq[LiteralType]): Seq[LiteralType] = {
    val allIntegers = values.forall(_ match {
      case IntegerLiteral(x) => true
      case DoubleLiteral(x) => false
      case BooleanLiteral(x) => false
    })
    val allBoolean = values.forall(_ match {
      case BooleanLiteral(x) => true
      case DoubleLiteral(_) => false
      case IntegerLiteral(_) => false
    })

    if (allIntegers || allBoolean)
      values
    else
      values.map(_.toDoubleLiteral)

  }

}

//object modeling {
//
//  def main(args: Array[String]): Unit = {
//
//    val baseNetwork = Network.fromXcf(new File("tmp/configuration.xcf"))
//
//    val profileDb = ProfileDB(
//      network = baseNetwork,
//      multicoreProfilePath = Paths.get("tmp/multicore-profile.xml"),
//      systemProfilePath = Paths.get("tmp/system-profile.xml"),
//      multicoreClock = 1.0 / 2.4)
//
//    val perfModel = PerformanceEstimator(profileDb, baseNetwork, 2)
//    perfModel.reportProfile
//    val network = baseNetwork withActors baseNetwork.actors.map(actor => Actor(actor.name, Random.nextInt(2), 2))
//
//    val t = perfModel.estimate(network)
//
//    println(t + "s")
//
//  }
//
//}

package estimation

import model.Network


sealed trait LiteralType{

  def identityValue: LiteralType
}

case class IntegerLiteral(value: Long) extends LiteralType {
  override def identityValue: LiteralType = IntegerLiteral(1)
}
case class DoubleLiteral(value: Double) extends LiteralType {
  override def identityValue: LiteralType = DoubleLiteral(1.0)
}
case class BooleanLiteral(value: Boolean) extends LiteralType {
  override def identityValue: LiteralType = BooleanLiteral(true)
}



case class Variable(name: String, coeff: Option[LiteralType] = None) {

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
    case Expression(_, _) => evaluate(formula)
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
  def evaluate(expr: Expression): Formula = {
    val op = expr.op
    val args = expr.args
    val evaluatedArgs = args.map(evaluate)
    val allLiterals = evaluatedArgs.forall(a => a match {
      case Lit(_) => true
      case _ => false
    })
    if (allLiterals) {
      val cArgs = evaluatedArgs.map(_.asInstanceOf[Lit].value.l)
      val foldedVal = constantEvaluator(op, cArgs)
      Lit(Literal(foldedVal))
    } else
      expr
  }

  lazy val constantEvaluator: PartialFunction[(Operator, Seq[LiteralType]), LiteralType] = {
    case (AddOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => IntegerLiteral(x.value  + y.value)
    case (AddOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => DoubleLiteral(x.value + y.value)
    case (SubOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => IntegerLiteral(x.value + y.value)
    case (SubOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => DoubleLiteral(x.value + y.value)
    case (MultiplyOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => IntegerLiteral(x.value + y.value)
    case (MultiplyOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => DoubleLiteral(x.value + y.value)
    case (MaxOperator, xs: Seq[IntegerLiteral]) => xs.maxBy(_.value)
    case (MaxOperator, xs: Seq[DoubleLiteral]) => xs.maxBy(_.value)
    case (SumOperator, xs: Seq[IntegerLiteral]) => IntegerLiteral(xs.map(_.value).sum)
    case (SumOperator, xs: Seq[DoubleLiteral]) => DoubleLiteral(xs.map(_.value).sum)
    case (EqualityOperator, Seq(x: IntegerLiteral, y: IntegerLiteral)) => BooleanLiteral(x.value == y.value)
    case (EqualityOperator, Seq(x: DoubleLiteral, y: DoubleLiteral)) => BooleanLiteral(x.value == y.value)
    case (EqualityOperator, Seq(x:BooleanLiteral, y: BooleanLiteral)) => BooleanLiteral(x.value == y.value)
    case _ => throw new RuntimeException("Unsupported constants evaluation")
  }

}

object modeling {

  def main(args: Array[String]): Unit = {

    val v1 = Variable("v1")
    val v2 = Variable("v2")
    val l1 = Literal(IntegerLiteral(1))
    val l2 = Literal(IntegerLiteral(2))

    val expr: Expression = Formula.Max(Seq((v1 + l1) + (v2 + l2)))
    val constExpr = Formula.substitute(expr)(Map("v1" -> 1, "v2" -> 2))
    val value = Formula.evaluate(constExpr)

    val tmp = 1

  }




}

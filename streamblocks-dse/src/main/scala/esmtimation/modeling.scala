package esmtimation



sealed trait Types {

}
case class IntegerType() extends Types
case class DoubleType() extends Types

case class Variable[T<:Types](name: String, value: T, lowerBound: T, upperBound: T) {


}

case class LinearExpression()


object modeling {

  def main(args: Array[String]): Unit = {


  }

}

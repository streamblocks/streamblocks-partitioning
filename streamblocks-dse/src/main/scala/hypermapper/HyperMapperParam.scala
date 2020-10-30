package hypermapper

import scala.:+
import scala.math._
import scala.util.Random
sealed trait HMParamType {
  def getTypeString: String
  override def toString: String = getTypeString
  def getRange: String

}

case class HMIntegerType[T] private (value: T, range: (T, T)) extends HMParamType {
  def getTypeString: String = "integer"
  def minValue: T = range._1
  def maxValue: T = range._2
  def getRange: String = "[" + minValue.toString + ", " + maxValue.toString + "]"
}


case object HMEmptyType extends HMParamType {
  override def getRange: String = "None"

  override def getTypeString: String = "None"

  override def toString: String = "None"

}
sealed trait HMParam {
  def toString: String
  def getType: HMParamType
  def getValue: String

}

case class HMAsymmetricPartitionParam(name: String, value: Int, size: Int) extends  HMParam {
  override def toString: String = name
  override def getType: HMIntegerType[Int] = HMIntegerType[Int](value, (0, size - 1))

  override def getValue: String = value.toString

}
case class HMSymmetricPartitionParam(name: String, value: BigInt, size: BigInt) extends HMParam {
  override def toString: String = name
  override def getType: HMIntegerType[BigInt] = HMIntegerType[BigInt](value, (0, size - 1))

  override def getValue: String = value.toString

}

case object HMEmptyParam extends HMParam {
  override def getType: HMParamType = HMEmptyType
  override def getValue: String = "None"
}

object HMSymmetricPartitionParam {

  implicit class ExtendedBigInt(v: BigInt) {
    def sqrt: BigInt = BigInt(v.bigInteger.sqrt())
    def biCompose: (BigInt, BigInt) = {
      def loop(ix: BigInt): (BigInt, BigInt) = {
        if (ix == 1) {
          (ix, v)
        } else {
          if (v % ix == 0) {
            (v / ix, ix)
          } else {
            loop(ix - 1)
          }
        }
      }
      loop(v.sqrt)
    }

    def ployCompose(threshold: Int): Seq[Int] = {

      def checkAndBreak(bigValue: BigInt): Seq[Int] = {
        if (bigValue > BigInt(threshold)) {
          val (d1, d2) = bigValue.biCompose
          if (d2 == bigValue)
            throw new RuntimeException("Failed breaking BigInt")
          else {
            checkAndBreak(d1) ++ checkAndBreak(d2)
          }
        } else {
          Seq(bigValue.toInt)
        }
      }
      checkAndBreak(v)
    }


    def polyComposeApprox(threshold: Int): (Seq[Int], BigDecimal) = {

      def checkAndBreak(bigValue: BigInt): Seq[Int] = {
        if (bigValue > BigInt(threshold)) {
          checkAndBreak(bigValue.sqrt) ++ checkAndBreak(bigValue.sqrt)
        } else {
          Seq(bigValue.toInt)
        }
      }
      val chunks = checkAndBreak(v)
      val approx = chunks.map(BigInt(_)).reduce(_*_)
      val error = BigDecimal(v - approx) / BigDecimal(v) * 100.0
      (chunks, error)
    }
  }


  def chunked(name: String, size: BigInt, random: Boolean = false): Seq[HMSymmetricPartitionParam] = {
    val (approxDivisors: Seq[Int], _) = size.polyComposeApprox(utils.Constants.HM_VALUE_THRESHOLD)
    def generateParam(sizeSeq: Seq[Int], index: Int): Seq[HMSymmetricPartitionParam] = sizeSeq match {
      case Seq() => Seq()
      case sz :: tail => HMSymmetricPartitionParam(name + "_" + index, if (random) Random.nextInt(sz) else 0, sz) +: generateParam(tail, index + 1)
    }
    generateParam(approxDivisors, 0)
  }
}



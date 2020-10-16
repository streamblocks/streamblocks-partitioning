package hypermapper

sealed trait HMParamType {
  def getTypeString: String
  override def toString: String = getTypeString
  def getRange: String
}

case class HMIntegerParamType private (value: Int, range:(Int, Int)) extends  HMParamType {
  def getTypeString: String = "integer"
  def getRange: String = "[" + range._1 + ", " + range._2 + "]"
  def toInt: Int = value
  def maxValue = range._2
  def minValue: Int = range._1

  def matchRange(that: HMIntegerParamType): Boolean = this.minValue == that.minValue && this.maxValue == that.maxValue
  def increment: HMIntegerParamType =
    if (this.toInt + 1 > this.maxValue)
      HMIntegerParamType(this.minValue, this.range)
    else
      HMIntegerParamType(this.toInt + 1, this.range)

}


case class HMIntegerType[T] private (value: T, range: (T, T)) extends HMParamType {
  def getTypeString: String = "integer"
  def minValue = range._1
  def maxValue = range._2
  def getRange: String = "[" + minValue.toString + ", " + maxValue.toString + "]"
}
object HMIntegerParamType {

  def apply(value: Int, range: (Int, Int)): HMIntegerParamType = {
    if (value > range._2 || value < range._1)
      throw new RuntimeException("Value" + value + " out of range(" + range._1 + ", " + range._2 + ")")
    else
      new HMIntegerParamType(value, range)

  }

}


sealed trait HMParam {
  def toString: String
  def getType: HMParamType
  def getValue: String

}

case class HMPartitionParam(name: String, affinity: Int, numCores: Int) extends HMParam {

  override def toString: String = name
  override def getType: HMIntegerParamType = HMIntegerParamType(affinity, (0, numCores))

  override def getValue: String = affinity.toString

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



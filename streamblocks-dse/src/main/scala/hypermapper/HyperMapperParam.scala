package hypermapper

sealed trait HMParamType {
  def getType: String
  override def toString: String = getType
}

case class HMIntegerParamType private (value: Int, range:(Int, Int)) extends  HMParamType {
  def getType: String = "integer"
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
}

case class HMPartitionParam(name: String, affinity: Int, numCores: Int) extends HMParam {

  override def toString: String = name
  override def getType: HMIntegerParamType = HMIntegerParamType(affinity, (0, numCores))

}



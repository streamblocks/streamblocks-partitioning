package model

case class Connection(srcActor: String, srcPort: String, dstActor: String, dstPort: String,
                      size: Option[Int] = None, capacity: Option[Int] = None) {
  def ==(that: Connection): Boolean =
    (this.srcActor == that.srcActor) &&
      (this.srcPort == that.srcPort) &&
      (this.dstActor == that.dstActor) &&
      (this.dstPort == that.dstPort)
  override def toString: String = s"${srcActor}.${srcPort}-->${dstActor}.${dstPort}"

}
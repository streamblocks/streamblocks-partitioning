package estimation

import java.io.File

import model.{Actor, Connection, Network}
import java.nio.file.{Files, Path}

class ProfileDB( val network: Network,
                 val multicoreProfilePath: File,
                 val systemProfilePath: File,
                 val multicoreClock: Double,
                 val acceleratorProfilePath: Option[File] = None,
                 val oclProfilePath: Option[File] = None,
                 val acceleratorClock: Option[Double] = None)
{

  private val multicoreDB: MulticoreProfile =
    MulticoreProfile(
      profilePath = multicoreProfilePath,
      systemProfilePath = systemProfilePath,
      network = network
    )
  private val acceleratorDB: Option[AcceleratorProfile] =
    if (acceleratorProfilePath.isDefined && oclProfilePath.isDefined && acceleratorClock.isDefined) {
      Some(
        AcceleratorProfile(
        profilePath = acceleratorProfilePath.get,
        systemProfilePath = oclProfilePath.get,
        network = network))
    } else {
      None
    }


  def apply(actor: Actor)(t: ProfileDB.ProfileType): Double = t match {
    case ProfileDB.ProfileType.HW =>
      if (acceleratorDB.isDefined && acceleratorClock.isDefined)
        acceleratorDB.get(actor) * acceleratorClock.get
      else
        0.0
    case ProfileDB.ProfileType.SW =>
      multicoreDB(actor) * multicoreClock

  }

  /**
   * Returns the multicore communication cost
   * @param connection
   * @return
   */
  def apply(connection: Connection): (Double, Double) = multicoreDB(connection) match {
    case (intra, inter) => (intra * multicoreClock, inter * multicoreClock)
  }


}

object ProfileDB {
  object ProfileType extends Enumeration {
    type ProfileType = Value
    val HW, SW = Value
  }
  type ProfileType = ProfileType.ProfileType

  def apply(network: Network,
            multicoreProfilePath: File,
            systemProfilePath: File,
            multicoreClock: Double,
            acceleratorProfilePath: Option[File] = None,
            oclProfilePath: Option[File] = None,
            acceleratorClock: Option[Double] = None): ProfileDB =
    new ProfileDB(
      network,
      multicoreProfilePath,
      systemProfilePath,
      multicoreClock,
      acceleratorProfilePath,
      oclProfilePath,
      acceleratorClock)
}

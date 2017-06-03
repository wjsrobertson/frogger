package net.xylophones.frogger

object Config {
  val gameWidth = 512
  val gameHeight = 1200
  val frogWidth = 22
  val frogHeight = 22
  val channelHeight = 32
  val channelsHeight = channelHeight * 12
  val scorePanelHeight = 32 * 3
  val homeHeight = 16 * 3
  val bottomPanelHeight = 16 + 32
  val frogMinY = scorePanelHeight
  val frogMaxY = scorePanelHeight + homeHeight + channelsHeight - (channelHeight - frogHeight) / 2
  val frogDeathTime = 28
  val timeImageWidth = 64
  val timeHeight = 16
  val statusHeight = 16
  val levelTimeLimitMs = 60000
}

trait ModelUpdater {
  def update(model: Model): Model
}

object ModelUpdaters {
  val updaters = Seq(ChannelUpdater, FrogMoveUpdater, FrogChannelLander , FrogPositionConstrainer, FrogCollisionChecker)
}

object FrogMoveUpdater extends ModelUpdater {
  def update(model: Model): Model = {
    val userDirection = UserInput.direction()

    val jumpDirection: Option[Direction.Dir] =
      if (model.frogJumpTimer > 0) Some(model.frogFacing)
      else if (userDirection.isDefined) userDirection
      else None

    val (fp, ff, ft) = jumpDirection match {
      case Some(d) =>
        val scale = 8
        val newFrogPosition = model.frogPosition.add(d.vector.scale(scale))
        val newFrogDirection = d
        val newFrogTimer = if (model.frogJumpTimer >= 1) model.frogJumpTimer - 1 else 3

        (newFrogPosition, newFrogDirection, newFrogTimer)
      case _ => (model.frogPosition, model.frogFacing, model.frogJumpTimer)
    }

    model.copy(frogPosition = fp, frogFacing = ff, frogJumpTimer = ft)
  }
}

object FrogPositionConstrainer extends ModelUpdater {
  def update(model: Model): Model = {
    import Config._

    val yConstrained = model.frogPosition match {
      case v: Vector if v.x < 0 => Vector(0, v.y)
      case v: Vector if v.x + frogWidth > gameWidth => Vector(gameWidth - frogWidth, v.y)
      case _ => model.frogPosition
    }

    val xyConstrained = yConstrained match {
      case v: Vector if (v.y + frogHeight) > frogMaxY => Vector(v.x, frogMaxY - frogHeight)
      case v: Vector if v.y < frogMinY => Vector(v.x, frogMinY)
      case _ => yConstrained
    }

    model.copy(frogPosition = xyConstrained)
  }
}

object FrogChannelLander extends ModelUpdater {
  def update(model: Model): Model = {

    val landingChannel = model.channelsWithPositions()
      .find(x => ChannelCollisionChecker.isLanding(x._1, x._2, model.layers.frog, model.frogPosition))
      .map(_._1)

    landingChannel.map(ch => ch.velocity)
      .map(vel => model.frogPosition.add(vel, 0))
      .map(fp => model.copy(frogPosition = fp))
      .getOrElse(model)
  }
}

object FrogCollisionChecker extends ModelUpdater {
  def update(model: Model): Model = {

    val isDeadlyCollision = model.frogDeathTimer == 0 &&
      model.channelsWithPositions()
      .exists(chp => ChannelCollisionChecker.isDeadlyCollision(chp._1, chp._2, model.layers.frog, model.frogPosition))

    if (isDeadlyCollision) {
      model.copy(frogDeathTimer = Config.frogDeathTime)
    } else if (model.frogDeathTimer > 0) {
      model.copy(frogDeathTimer = model.frogDeathTimer - 1)
    } else model
  }
}

object TimerUpdater extends ModelUpdater {
  def update(model: Model): Model = {
    if (model.levelDurationMs() > Config.levelTimeLimitMs)
      model.copy(frogDeathTimer = Config.frogDeathTime)
    else model
  }
}

object ChannelUpdater extends ModelUpdater {
  def update(model: Model): Model = {
    val channelPositions: Seq[Vector] = (model.layers.all zip model.positions)
      .filter(_._1.isInstanceOf[Channel])
      .map(_._2)

    val cp = updatePositions(model.layers.channels, channelPositions)
    val ap = Layers.allPositions(cp)

    model.copy(positions = ap)
  }

  private def updatePositions(channels: Seq[Channel], positions: Seq[Vector]): Seq[Vector] = {
    channels zip positions map { case (c, p) =>
      val x = shunt(c, p.x + c.velocity)
      Vector(x, p.y)
    }
  }

  private def shunt(channel: Channel, x: Int): Int = {
    import channel._

    val mid = x + width / 2

    if (velocity > 0 && x > 0) x - width / 2
    else if (velocity < 0 && mid < 0) x + width / 2
    else x
  }
}

/*
Every safe step scores 10 points, and every frog arriving home scores 50 points plus 10 per unused second.
Guiding a lady frog home or eating a fly scores 200 points), and when all
5 frogs are home to end the level the player earns 1,000 points.
Players earn an extra life at 10,000 or 20,000 points, and none thereafter.
 */

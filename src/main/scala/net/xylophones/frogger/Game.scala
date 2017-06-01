package net.xylophones.frogger

import org.scalajs.dom.raw.HTMLImageElement
import org.scalajs.dom.{CanvasRenderingContext2D, document}

class Image(src: String) {
  val element = document.createElement("img").asInstanceOf[HTMLImageElement]
  element.src = src
}

case class Vector(x: Int, y: Int) {
  def add(X: Int, Y: Int): Vector = Vector(X + x, Y + y)

  def add(v: Vector): Vector = add(v.x, v.y)

  def scale(s: Int) = Vector(s * x, s * y)

  override def toString(): String = s"($x, $y)"
}

trait Rectangular {
  def x: Int = 0

  def y: Int = 0

  def width: Int

  def height: Int

  def padding: Int = 0

  def centre: (Int, Int) = (x + width / 2, y + height / 2)

  def intersects(position: Vector, other: Rectangular, otherPosition: Vector) = {
    val minX = position.x + x + padding
    val maxX = position.x + x + width - padding
    val minY = position.y + y + padding
    val maxY = position.y + y + height - padding

    val otherMinX = otherPosition.x + other.x + other.padding
    val otherMaxX = otherPosition.x + other.x + other.width - other.padding
    val otherMinY = otherPosition.y + other.y + other.padding
    val otherMaxY = otherPosition.y + other.y + other.height - other.padding

    ((minY > otherMinY && minY < otherMaxY) || (maxY > otherMinY && maxY < otherMaxY)) &&
      ((minX > otherMinX && minX < otherMaxX) || (maxX > otherMinX && maxX < otherMaxX))
  }

  // position and point are global coords
  def contains(position: Vector, point: Vector) = {
    // println(s"($x,$y) / $position / $point / $otherPosition")
    val px = point.x
    val py = point.y

    px >= x + position.x &&
      py >= y + position.y &&
      px < (x + position.x + width) &&
      py < (y + position.y + height)
  }
}

class Rectangle(override val x: Int,
                override val y: Int,
                val width: Int,
                val height: Int,
                override val padding: Int = 0) extends Rectangular {

  override def toString(): String = s"$x, $y, $width, $height, $padding"
}

abstract class Layer(val width: Int = 0,
                     val height: Int = 0) {

  def draw(context: CanvasRenderingContext2D, offset: Vector)
}

trait CellCoords {
  def col: Int

  def row: Int
}

case class Tile(col: Int, row: Int, id: Int = 0)

class TiledImage(val image: Image, val tileWidth: Int, val tileHeight: Int)

class TiledLayer(protected val tileImage: TiledImage, protected val rows: Int, val columns: Int, contents: Array[Array[Tile]]) extends Layer {
  protected val img = tileImage.image.element

  override val height = rows * tileImage.tileHeight

  override val width = columns * tileImage.tileWidth

  def getCell(row: Int, column: Int) = contents(row)(column)

  val rectangles: Seq[Rectangle with CellCoords] = for {
    r <- 0 until rows
    c <- 0 until columns
    rX = r * tileImage.tileWidth
    rY = c * tileImage.tileHeight
  } yield new Rectangle(rX, rY, tileImage.tileWidth, tileImage.tileHeight) with CellCoords {
    val col = c
    val row = r
  }

  override def draw(context: CanvasRenderingContext2D, position: Vector) = {
    for (row <- 0 until rows) {
      for (column <- 0 until columns) {
        val tile = contents(row)(column)

        val tileCol = tile.col
        val tileRow = tile.row

        val imgXOffset = tileCol * tileImage.tileWidth
        val imgYOffset = tileRow * tileImage.tileHeight

        val canvasOffsetX = position.x + (tileImage.tileWidth * column)
        val canvasOffsetY = position.y + (tileImage.tileHeight * row)

        context.drawImage(img, imgXOffset, imgYOffset, tileImage.tileWidth, tileImage.tileHeight, canvasOffsetX, canvasOffsetY, tileImage.tileWidth, tileImage.tileHeight)
      }
    }
  }
}

object HorizontalCompositeLayout {
  def layout(position: Vector, children: Seq[Layer]): Seq[Vector] = {
    val localOffsets = children.map(_.width).foldLeft(Seq(0)) {
      (acc: Seq[Int], h: Int) => (acc.head + h) +: acc
    }
      .reverse
      .dropRight(1)

    val offsets = localOffsets.map(_ + position.x)

    children zip offsets map {
      case (c, ox) => Vector(ox, position.y)
    }
  }
}

object VerticalCompositeLayout {
  def layout(position: Vector, children: Seq[Layer]): Seq[Vector] = {
    val localOffsets = children.map(_.height).foldLeft(Seq(0)) {
      (acc: Seq[Int], h: Int) => (acc.head + h) +: acc
    }
      .reverse
      .dropRight(1)

    val offsets = localOffsets.map(_ + position.y)

    children zip offsets map {
      case (c, oy) => Vector(position.x, oy)
    }
  }
}

class BackgroundLayer(val w: Int, val h: Int, colour: String) extends Layer(w, h) {
  override def draw(context: CanvasRenderingContext2D, position: Vector) = {
    context.fillStyle = colour
    context.strokeStyle = colour
    context.fillRect(position.x, position.y, width, height)
  }
}

class Sprite(image: Image, frameWidth: Int) extends Rectangular {
  override val padding = Math.max(1, frameWidth / 4)
  private val img = image.element
  private val numFrames: Int = image.element.width / frameWidth

  def draw(context: CanvasRenderingContext2D, position: Vector, frame: Int) = {
    val imgXOffset = frame * frameWidth
    context.drawImage(img, imgXOffset, 0, frameWidth, img.height, position.x, position.y, frameWidth, img.height)
  }

  val width = frameWidth

  val height = img.height

  val midPoint = Vector(width / 2, height / 2)

}

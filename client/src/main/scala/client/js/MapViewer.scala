package client.js

import org.scalajs.dom.raw.CanvasRenderingContext2D
import shared.car.CarsList
import shared.geometry._
import shared.map.RoadMap

import scala.util.Random

class MapViewer(context: CanvasRenderingContext2D) {
  private val MapCoordinatesRange = 1000.0
  private val PixelsMapRange = 800.0
  private val PixelsForMargins = 50.0

  private val PixelsPerMapStep = PixelsMapRange / MapCoordinatesRange

  private val HalfCrossingSize = 20.0
  private val HalfCarSize = 5.0

  private val ColorPimpPurple = "#803CA2"
  private val ColorBlack = "#000000"

  def drawMap(map: RoadMap): Unit = {
    context.font = HalfCrossingSize + "px Arial"

    map.crossings.foreach(crossing => {
      drawCrossing(crossing.coordinates, crossing.name)
    })

    map.roads.foreach(road => {
      val allPoints = road.start.coordinates :: road.bendingPoints ::: road.end.coordinates :: List.empty
      allPoints.sliding(2).foreach { case List(start, end) => drawRoad(start, end) }
    })
  }

  def drawCrossing(location: Coordinates, name: String): Unit = {
    val textX = scaleValue(location.x) + 1.5 * HalfCrossingSize
    val textY = scaleValue(location.y) - 0.5 * HalfCrossingSize

    drawCircle(scaleCoordinates(location), ColorPimpPurple, HalfCrossingSize)
    context.fillText(name, textX, textY)
  }

  def drawRoad(start: Coordinates, end: Coordinates): Unit = {
    drawLine(scaleCoordinates(start), scaleCoordinates(end))

    // tmp
    val colorsList = List("green", "red", "blue", "yellow", "pink")

    drawCar((start.x + end.x) / 2.0 >< (start.y + end.y) / 2.0, colorsList(Random.nextInt(colorsList.size)))
    // end tmp
  }

  def drawCars(carsList: CarsList): Unit = carsList.cars.foreach(car => drawCar(car.location, car.color))

  def drawCar(location: Coordinates, color: String): Unit = drawRect(scaleCoordinates(location), color, HalfCarSize)

  def drawCircle(middle: Coordinates, color: String, radius: Double): Unit = {
    context.fillStyle = color

    context.beginPath
    context.arc(middle.x, middle.y, radius, 0, 2 * Math.PI)
    context.closePath
    context.fill
    context.stroke

    context.fillStyle = ColorBlack
  }

  def drawRect(middle: Coordinates, color: String, halfRectSide: Double): Unit = {
    val rectX = middle.x - halfRectSide
    val rectY = middle.y - halfRectSide
    val rectSide = 2 * halfRectSide

    context.fillStyle = color

    context.fillRect(rectX, rectY, rectSide, rectSide)
    context.strokeRect(rectX, rectY, rectSide, rectSide)

    context.fillStyle = ColorBlack
  }

  def drawLine(start: Coordinates, end: Coordinates): Unit = {
    context.beginPath
    context.moveTo(start.x, start.y)
    context.lineTo(end.x, end.y)
    context.closePath
    context.stroke
  }

  def scaleCoordinates(coordinates: Coordinates): Coordinates = scaleValue(coordinates.x) >< scaleValue(coordinates.y)

  def scaleValue(value: Double): Double = PixelsForMargins + value * PixelsPerMapStep
}


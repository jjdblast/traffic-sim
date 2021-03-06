package system.simulation.strategy

import akka.actor.ActorRef
import shared.map.Road
import system.simulation.Car

import scala.collection.immutable.Queue

case class RandomLightsStrategy(waitingCars: Queue[Car], allowedRoads: List[(ActorRef, Int)]) extends CrossingStrategy {
  override def addCar(car: Car): CrossingStrategy =
    copy(waitingCars = waitingCars enqueue car)

  override def nextCar(blockedRoads: Set[Road]): (Option[Car], CrossingStrategy) = {
    val waitingCar = waitingCars.find(car => car.supervisor == allowedRoads.head._1 && (car.route.isEmpty || !blockedRoads.contains(car.route.head)))
    waitingCar match {
      case Some(car) =>
        (Some(car), copy(waitingCars.filterNot(_ == waitingCar.get), iterateLights(allowedRoads)))
      case None =>
        (None, copy(allowedRoads = iterateLights(allowedRoads)))
    }
  }

  def iterateLights(roads: List[(ActorRef, Int)]): List[(ActorRef, Int)] = {
    roads match {
      case (actorRef, lightsTick) :: tail if lightsTick > 0 =>
        (actorRef, lightsTick - 1) :: tail
      case (actorRef, lightsTick) :: tail if lightsTick == 0 =>
        tail :+(actorRef, RandomLightsStrategy.randDuration)
      case _ => roads
    }
  }
}

object RandomLightsStrategy {
  def randDuration = scala.util.Random.nextInt(100) + 25

  def apply(allowedRoads: List[ActorRef]): ConstantLightsStrategy = new ConstantLightsStrategy(Queue.empty, allowedRoads.map(ref => (ref, randDuration)))
}

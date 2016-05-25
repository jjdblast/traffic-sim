package system.simulation

import akka.actor.{Actor, ActorRef, Props}
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsString}
import shared.map.{Crossing, Road, RoadMap}
import system.simulation.SimulationManager.{CarRemoved, CarSpawned, CarsMoved, UpdateQueueCreated}

import scala.language.postfixOps
import utils.MapUtils._
import shared.car.{CarsList, Car => CarDAO}

object SimulationManager {

  case class UpdateQueueCreated(actorRef: ActorRef)

  case class CarsMoved(tick: Long, cars: Seq[Car]) extends StateChangedMessage

  case class CarRemoved(tick: Long, car: Car) extends StateChangedMessage

  case class CarSpawned(tick: Long, car: Car, target: Crossing) extends StateChangedMessage

}

class SimulationManager(map: RoadMap, outputStream: ActorRef) extends Actor {

  val crossingAgentsMap: Map[ActorRef, Crossing] = map.crossings map { c =>
    val actorRef = context actorOf Props(classOf[CrossingAgent], c)
    (actorRef, c)
  } toMap

  val roadsAgentsMap: Map[ActorRef, Road] = map.roads map { r =>
    val actorRef = context actorOf Props(classOf[RoadAgent], r)
    (actorRef, r)
  } toMap

  override def receive: Receive = gatheringQueuesInfo(Map(), Map())

  def gatheringQueuesInfo(roadQueues: Map[Road, ActorRef], crossingQueues: Map[Crossing, ActorRef]): Receive = {
    case UpdateQueueCreated(queue) =>

      val (newRoadQueues, newCrossingQueues) = if (crossingAgentsMap contains sender) {
        (roadQueues, crossingQueues + (crossingAgentsMap(sender) -> queue))
      } else if (roadsAgentsMap contains sender) {
        (roadQueues + (roadsAgentsMap(sender) -> queue), crossingQueues)
      } else {
        (roadQueues, crossingQueues)
      }
      if (newCrossingQueues.size == crossingAgentsMap.size && newRoadQueues.size == roadsAgentsMap.size) {
        initialiseAll(newRoadQueues, newCrossingQueues)
        context become waitingForAck(crossingAgentsMap.keySet ++ roadsAgentsMap.keySet)
      } else {
        context become gatheringQueuesInfo(newRoadQueues, newCrossingQueues)
      }
  }

  def initialiseAll(roadQueues: Map[Road, ActorRef], crossingQueues: Map[Crossing, ActorRef]): Unit = {
    roadsAgentsMap foreach { case (actorRef, road) =>
      actorRef ! RoadAgent.RoadInit(crossingQueues(road.start), crossingQueues(road.end))
    }

    crossingAgentsMap foreach { case (actorRef, crossing) =>
      actorRef ! CrossingAgent.CrossingInit(roadQueues filterKeys crossing.roads.toSet, roadQueues filterKeys crossing.reverseRoads.toSet)
    }
  }

  def waitingForAck(notConfirmedActors: Set[ActorRef]): Receive = {
    case Ack =>
      val newNotConfirmedActors = notConfirmedActors - sender
      if (newNotConfirmedActors.isEmpty) {
        startSimulation()
        // TODO spawn test car
        context become gatheringSimulationData(Map() withDefaultValue List.empty,
          Map() withDefaultValue (crossingAgentsMap.size + roadsAgentsMap.size), Map.empty)
      } else {
        context become waitingForAck(newNotConfirmedActors)
      }
  }

  def startSimulation(): Unit = roadsAgentsMap.keys ++ crossingAgentsMap.keys foreach {
    _ ! Start
  }

  def gatheringSimulationData(messages: Map[Long, List[StateChangedMessage]], ticks: Map[Long, Int],
                              cars: Map[String, CarDAO]): Receive = {
    case msg: StateChangedMessage =>
      val current = msg.tick
      val (newTicks, newValue) = ticks.adjustWithValue(current) {
        _ - 1
      }
      val newMessages = messages.adjust(current) {
        msg :: _
      }
      if (newValue == 0) {
        val newCars: Map[String, CarDAO] = newMessages(current).foldLeft(cars) { case (acc, change) =>
          change match {
            case CarSpawned(_, car, _) => acc + (car.id -> car)
            case CarRemoved(_, car) => acc - car.id
            case CarsMoved(_, movedCars) => acc ++ movedCars.map(c => c.id -> (c: CarDAO)).toMap
            case NoOp(_) => acc
          }
        }
        context.parent ! CarsList(newCars.values.toList)
        context become gatheringSimulationData(messages - current, ticks - current, newCars)
      } else {
        context become gatheringSimulationData(newMessages, newTicks, cars)
      }
  }
}
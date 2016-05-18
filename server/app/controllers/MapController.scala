package controllers

import akka.pattern.Patterns.ask
import com.google.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.concurrent.Promise
import play.api.mvc._
import shared.MapApi
import shared.map.RoadMap
import system.MapAgent._
import system.{ActorManager, ApiImplementation, MyServer}
import upickle._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class MapController @Inject()(actorManager: ActorManager, apiImplementation: ApiImplementation) extends Controller {

  def timeout: FiniteDuration = 3 seconds

  def showAll = Action.async { request =>
    val futureResult = ask(actorManager.mapAgent, GetMap, timeout * 2).collect {
      case map: RoadMap => Ok(views.html.allRoads(map))
    }

    val timeoutResult = Promise.timeout(RequestTimeout("timeout"), timeout)
    Future.firstCompletedOf(Seq(futureResult, timeoutResult))
  }

  def shortestRoute(start: String, end: String) = Action.async { request =>
    val futureResult = ask(actorManager.mapAgent, ShortestRouteRequest(start, end), timeout * 2).collect {
      case UnknownNodes => NotFound("Unknown crossings")
      case Unreachable => NotFound("Unreachable")
      case Route(roads) => Ok(views.html.path(start, end, roads))
    }

    val timeoutResult = Promise.timeout(RequestTimeout("timeout"), timeout)
    Future.firstCompletedOf(Seq(futureResult, timeoutResult))
  }

  def mapApi(apiMethod: String) = Action.async { request =>
    MyServer.route[MapApi](apiImplementation)(autowire.Core.Request(
      apiMethod.split("/"),
      upickle.json.read(request.body.asText.getOrElse("{}")).asInstanceOf[Js.Obj].value.toMap))
      .map(upickle.json.write(_))
      .map(Ok(_))
  }
}

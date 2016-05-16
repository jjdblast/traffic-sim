package client.js

import autowire._
import org.scalajs.dom
import shared.map.MapApi
import upickle.Js
import upickle.Js.Value
import upickle.default._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExport
import scala.util.{Failure, Success}
import scalatags.JsDom.all._

object Client extends Client[Js.Value, Reader, Writer] {
  def read[Result: Reader](p: Js.Value) = upickle.default.readJs[Result](p)

  def write[Result: Writer](r: Result) = upickle.default.writeJs(r)

  override def doCall(req: Request): Future[Js.Value] = {
    dom.ext.Ajax.get(
      url = "/api/" + req.path.mkString("/"),
      data = upickle.json.write(Js.Obj(req.args.toSeq: _*))
    ).map(_.responseText)
      .map(upickle.json.read)
  }
}


@JSExport
object FrontClient {
  @JSExport
  def main(): Unit = {
    val textContent = Client[MapApi].test().call().onComplete {
      case Success(s) => dom.document.getElementById("scalaMagicClientCode").textContent = s
      case Failure(s) => println("failure: " + s)
    }
    dom.document.body.appendChild(myContent)
    val webSocket = new dom.WebSocket("ws://localhost:9000/socket")
    webSocket.onopen = { (e: dom.Event) =>
      webSocket.send("hello")
    }
    webSocket.onmessage = { (e: dom.MessageEvent) =>
      dom.document.getElementById("websocketMessages").appendChild(li(e.data.toString).render)
    }

  }

  def myContent = div(
    h1(id := "title", "This is a title"),
    p("This is a proof that we can do awesome javascripting from client!! haha finally!")
  ).render
}

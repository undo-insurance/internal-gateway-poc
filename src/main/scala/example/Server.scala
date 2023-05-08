package example

import zio.ZIOAppDefault
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import caliban.AkkaHttpAdapter
import _root_.sttp.tapir.json.circe._
import zio._
import io.circe._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import akka.http.scaladsl.Http

object Server extends ZIOAppDefault {

  val requestInit: ZLayer[Any, Nothing, Gateway.RequestContext] =
    ZLayer.scoped(
      FiberRef.make(Option.empty[Gateway.Token])
    ) ++ ZLayer.scoped(
      FiberRef.make(Option.empty[Gateway.ClientIp])
    ) ++ ZLayer.scoped(
      FiberRef.make(Option.empty[Gateway.UserId])
    )
  implicit val actorSystem = ActorSystem()
  val adapter = AkkaHttpAdapter.default(actorSystem.dispatcher)

  val route = for {
    graph <- Gateway.graph
    interpreter <- graph.interpreter.orDie
    implicit0(runtime: zio.Runtime[Gateway.RequestContext]) <- ZIO.runtime
      .provideSomeLayer(requestInit)
  } yield {
    path("graphql") {
      post {
        entity(as[JsonObject]) { _ =>
          adapter.makeHttpService(
            interpreter
          )
        }
      }
    }
  }

  override def run = {

    for {
      route <- route
      bindingFuture <- ZIO.fromFuture { implicit ec =>
        Http().newServerAt("localhost", 8080).bind(route)
      }
      _ <- zio.Console.readLine
    } yield ()
  }

}

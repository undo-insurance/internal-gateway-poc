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

  val userContext = UserContext(name = "Poul Skipper")
  val userContextLayer: ZLayer[Any, Nothing, UserContext] = ZLayer.succeed(
    userContext
  )
  implicit val actorSystem = ActorSystem()
  val adapter = AkkaHttpAdapter.default(actorSystem.dispatcher)

  val route = for {
    graph <- Gateway.graph.provide(userContextLayer)
    interpreter <- graph.interpreter.orDie
    implicit0(runtime: zio.Runtime[UserContext]) <- ZIO.runtime
      .provideSomeLayer(userContextLayer)
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

    //   val
    //   println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    //   StdIn.readLine() // let it run until user presses return
    //   bindingFuture
    //     .flatMap(_.unbind()) // trigger unbinding from the port
    //     .onComplete(_ => system.terminate()) // and shutdown when done
  }

}

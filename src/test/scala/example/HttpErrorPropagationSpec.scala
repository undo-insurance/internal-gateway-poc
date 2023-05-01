package example

import zio.test._
import java.net.InetAddress

import scala.concurrent.duration._

import zio.FiberRef
import zio.ZIO
import zio.ZLayer
import zio.test._
import zio.test.akkahttp.RouteTestEnvironment
import zio.test.akkahttp.assertions._

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Codec
import io.circe.generic.semiauto._
import scala.concurrent.duration.FiniteDuration
import zio.Scope

object HttpErrorPropagationSpec extends ZIOSpecDefault {

  final case class QueryPayload(query: String)

  object QueryPayload {
    implicit val circeCodec: Codec.AsObject[QueryPayload] =
      deriveCodec[QueryPayload]
  }

  val akkaTimeout: FiniteDuration = 10.seconds

  val query = QueryPayload("query { sangriaError }")
  override def spec: Spec[Environment with TestEnvironment with Scope, Any] =
    suite("tests")(
      test(
        "POST /graphql to a public caliban query returns OK and some valid data"
      ) {
        for {
          route <- example.Server.route // TODO: should be scoped?
          implicit0(actorSystem: ActorSystem) <- ZIO.service[ActorSystem]
          assertion <- (
            Post("/graphql", query) ~> route
          ).flatMap(routeResult =>
            ZIO
              .fromFuture { _ =>
                routeResult.handled.get.entity.toStrict(akkaTimeout)
              }
              .map { response =>
                assertTrue(
                  routeResult.handled.get.status == OK &&
                    response.data.utf8String
                      .contains(
                        // TODO: Fix the expected type
                        """{"data":null,"errors":[{"message":"Internal server error","path":["sangriaError"],"locations":[{"line":1,"column":9}]}]}}"""
                      )
                )
              }
          )
        } yield assertion
      }
    ).provideSomeLayer(RouteTestEnvironment.environment)

}

package example

import caliban.{CalibanError, GraphQL, GraphQLInterpreter, ResponseValue}
import caliban.client._
import caliban.execution.Field
import caliban.tools.RemoteSchema
import caliban.tools.SchemaLoader
import caliban.tools.stitching.RemoteQuery
import caliban.tools.stitching.RemoteResolver
import caliban.tools.stitching.RemoteSchemaResolver
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._
import sangria.execution.Executor
import sangria.marshalling.circe._
import zio._

import scala.util.Success
import zio.stream.ZStream
import zio.stream.ZSink

import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.Path
import sangria.parser.QueryParser
import scala.annotation.meta.field
import caliban.tools.stitching.RemoteMutation
import java.util.UUID

final case class SangriaUserContext(
    token: Option[Gateway.Token],
    clientIp: Option[Gateway.ClientIp]
)

object Gateway {

  final case class Token(value: String)

  final case class ClientIp(value: String)

  final case class UserId(value: UUID)

  type AuthenticatedRequest = FiberRef[Option[UserId]]

  type ClientIpRequest = FiberRef[Option[ClientIp]]

  type AuthTokenRequest = FiberRef[Option[Token]]

  type RequestContext =
    AuthTokenRequest with ClientIpRequest with AuthenticatedRequest

  val graph: URIO[Any, GraphQL[RequestContext]] = for {
    pureCaliban <- ZIO.succeed(Caliban.schema)
    proxiedSangria <- introspectSangria.orDie
    graphQL = (pureCaliban |+| proxiedSangria)
  } yield graphQL

  private def authToken: ZIO[RequestContext, Nothing, Option[Token]] = ZIO
    .serviceWithZIO[FiberRef[Option[Token]]](_.get)

  private def clientIp: ZIO[RequestContext, Nothing, Option[ClientIp]] = ZIO
    .serviceWithZIO[FiberRef[Option[ClientIp]]](_.get)

  private def remoteResolver
      : RemoteResolver[RequestContext, Nothing, Field, ResponseValue] =
    RemoteResolver
      .fromFunctionM { field =>
        for {
          token <- authToken
          ip <- clientIp
          result <- ZIO
            .fromFuture(implicit ec =>
              Sangria.handleRequest(
                RemoteQuery(field).toGraphQLRequest.asJson.asObject.get,
                SangriaUserContext(token, ip)
              )
            )
            .flatMap(
              ZIO
                .fromTry(_)
                .flatMap(json =>
                  ZIO.fromEither(decode[ResponseValue](json.toString))
                )
            )
            .orDie
        } yield result
      }

  private val introspectSangria
      : ZIO[Any, Throwable, GraphQL[RequestContext]] = {
    for {
      introspectionResponseRaw <-
        ZIO
          .fromFuture { implicit ec =>
            Executor
              .execute(
                Sangria.schema,
                sangria.introspection.introspectionQuery,
                userContext = SangriaUserContext(None, None)
              )
          }
      introspectionResponse = introspectionResponseRaw.hcursor
        .downField("data")
        .focus
        .get
      remoteSchema <- ZIO.fromEither {
        caliban.tools.IntrospectionClient.introspection
          .decode(
            introspectionResponseRaw.spaces2
          )
          .map { case (document, _, _) => document }
      }
      remoteSchema <- ZIO
        .fromOption(RemoteSchema.parseRemoteSchema(remoteSchema))
        .unsome
        .someOrFailException
        .orDie
      remoteSchemaResolvers = RemoteSchemaResolver.fromSchema(remoteSchema)
      queryResolver = remoteResolver
      mutationResolver = remoteResolver
    } yield {
      remoteSchemaResolvers
        .proxy(
          queryResolver,
          Some(mutationResolver)
        )
    }
  }
}

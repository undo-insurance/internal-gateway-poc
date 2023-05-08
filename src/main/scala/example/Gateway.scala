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
import sttp.tapir.json.circe._

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
import scala.util.Try
import scala.tools.nsc.interactive.Response
import caliban.parsing.adt.LocationInfo

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
    proxiedSangria <- stitchSangria.orDie
    graphQL = (pureCaliban |+| proxiedSangria)
  } yield graphQL

  private def authToken: ZIO[RequestContext, Nothing, Option[Token]] = ZIO
    .serviceWithZIO[FiberRef[Option[Token]]](_.get)

  private def clientIp: ZIO[RequestContext, Nothing, Option[ClientIp]] = ZIO
    .serviceWithZIO[FiberRef[Option[ClientIp]]](_.get)

  private val customUnwrapper: RemoteResolver[
    Any,
    /*
     * The Caliban proxy method does not allow for multiple errors in the remote resolver
     * So we're only getting the first error returned from the stitched Sangria graph
     * This should be ok, as the clients are not currently fetching multiple fields anyway.
     *
     * If we need to support multiple errors returned, then let us speed up the caliban migration
     * */
    CalibanError.ExecutionError,
    ResponseValue,
    ResponseValue
  ] =
    RemoteResolver.fromFunctionM {
      case objectValueResponse @ ResponseValue.ObjectValue(fields) =>
        fields.find(_._1 == "errors") match {
          case None =>
            ZIO.succeed(
              fields.headOption.map(_._2).getOrElse(objectValueResponse)
            )
          case Some(error) => {
            val responseValueJson = error._2.asJson
            val message: String = responseValueJson.hcursor.downArray
              .downField("message")
              .focus
              .flatMap(_.asString)
              .getOrElse("Weird error from Sangria")
            val path: List[Either[String, Int]] = {
              val segments: Option[Vector[Json]] =
                responseValueJson.hcursor.downArray
                  .downField("path")
                  .focus
                  .flatMap(_.asArray)

              segments
                .map { paths =>
                  paths.map { jsonPath =>
                    (
                      jsonPath.asNumber.flatMap(_.toInt),
                      jsonPath.asString
                    ) match {
                      case (Some(int), _)    => Right(int)
                      case (_, Some(string)) => Left(string)
                      case _ =>
                        Left(
                          s"Error while parsing the path segments. Expected Int or String but got: ${jsonPath.noSpaces}"
                        )
                    }
                  }.toList
                }
                .getOrElse(List.empty)
            }
            val extensions: Option[ResponseValue.ObjectValue] =
              responseValueJson.hcursor.downArray
                .downField("extensions")
                .focus
                .flatMap { extensionsJson =>
                  val decoded: Decoder.Result[ResponseValue] =
                    extensionsJson.as[ResponseValue]
                  decoded.toOption.flatMap {
                    case objectValue @ ResponseValue.ObjectValue(_) =>
                      Some(objectValue)
                    case _ => None
                  }
                }
            ZIO.fail(
              CalibanError.ExecutionError(
                message,
                path,
                None,
                None,
                extensions
              )
            )
          }
        }
      case nonObjectValueResponse => ZIO.succeed(nonObjectValueResponse)
    }

  private def stitchSangria(): ZIO[Any, Throwable, GraphQL[RequestContext]] = {
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
      remoteQueryResolver = RemoteResolver
        .fromFunctionM((f: Field) =>
          (for {
            token <- authToken
            ip <- clientIp
            request <- ZIO.fromFuture(implicit ex =>
              Sangria.handleRequest(
                RemoteQuery(f).toGraphQLRequest.asJson.asObject.get,
                SangriaUserContext(token, ip)
              )
            )
            json <- ZIO.fromTry(request)
            response <- ZIO.fromEither(json.as[ResponseValue])
          } yield response).orDie
        )
      remoteMutationResolver = RemoteResolver.fromFunctionM((f: Field) =>
        (for {
          token <- authToken
          ip <- clientIp
          request <- ZIO.fromFuture(implicit ex =>
            Sangria.handleRequest(
              RemoteMutation(f).toGraphQLRequest.asJson.asObject.get,
              SangriaUserContext(token, ip)
            )
          )
          json <- ZIO.fromTry(request)
          response <- ZIO.fromEither(json.as[ResponseValue])
        } yield response).orDie
      )
    } yield {
      remoteSchemaResolvers
        .proxy(
          remoteQueryResolver >>> customUnwrapper,
          Some(
            remoteMutationResolver >>> customUnwrapper
          )
        )
    }
  }
}

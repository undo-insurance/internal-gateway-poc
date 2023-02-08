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
import zio.{UIO, ZIO}

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

object Gateway {
  val graph: UIO[GraphQL[Any]] = for {
    pureCaliban <- ZIO.succeed(Caliban.schema)
    proxiedSangria <- introspectSangria.orDie
    graphQL = (pureCaliban |+| proxiedSangria)
  } yield graphQL

  private val introspectSangria = {
    for {
      introspectionResponseRaw <-
        ZIO
          .fromFuture { implicit ec =>
            Executor
              .execute(
                Sangria.schema,
                sangria.introspection.introspectionQuery
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
    } yield {
      remoteSchemaResolvers
        .proxy(
          RemoteResolver
            .fromFunctionM((f: Field) =>
              ZIO
                .fromFuture(implicit ec =>
                  Sangria.handleRequest(
                    RemoteQuery(f).toGraphQLRequest.asJson.asObject.get
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
            ),
          Some(
            RemoteResolver.fromFunctionM((f: Field) =>
              ZIO
                .fromFuture(implicit ex =>
                  Sangria.handleRequest(
                    RemoteMutation(f).toGraphQLRequest.asJson.asObject.get
                  )
                )
                .flatMap(
                  ZIO
                    .fromTry(_)
                    .flatMap(json =>
                      ZIO.fromEither(decode[ResponseValue](json.toString()))
                    )
                )
                .orDie
            )
          )
        )
    }
  }
}

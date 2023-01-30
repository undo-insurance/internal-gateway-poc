package example

import caliban.ResponseValue
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
import zio.ZIO

import scala.util.Success

object Gateway {
  val interpreter = for {
    pureCaliban <- ZIO.succeed(Caliban.schema)
    proxiedSangria <- introspectSangria
    graphQL = (pureCaliban |+| proxiedSangria)
    interpreter <- graphQL.interpreter.orDie
  } yield interpreter

  val introspectSangria = {
    for {
      introspectionResponse <-
        ZIO
          .fromFuture { implicit ec =>
            Executor
              .execute(
                Sangria.schema,
                sangria.introspection.introspectionQuery
              )
              .map(Success.apply)
              .map(_.toEither)
              .map(_.map(_.hcursor.downField("data").focus))
          }
          .right
          .orElseFail(new RuntimeException("Oh no"))
          .someOrFailException
          .orDie
      schema <- SchemaLoader
        .fromString(introspectionResponse.noSpaces)
        .load
        .orDie
      remoteSchema <- ZIO
        .fromOption(RemoteSchema.parseRemoteSchema(schema))
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
          None
        )
    }
  }
}

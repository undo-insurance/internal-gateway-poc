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
import zio.stream.ZStream
import zio.stream.ZSink
import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.Path
import sangria.parser.QueryParser

object Gateway {
  val interpreter = for {
    pureCaliban <- ZIO.succeed(Caliban.schema)
    proxiedSangria <- introspectSangria
    graphQL = (pureCaliban |+| proxiedSangria)
    interpreter <- graphQL.interpreter.orDie
  } yield interpreter

  val introspectionStringFromCaliban =
    """query{__schema{queryType{name} mutationType{name} subscriptionType{name} types{kind name description fields(includeDeprecated:true){name description args{name description type{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{name}}}}}}}}} defaultValue} type{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{name}}}}}}}}} isDeprecated deprecationReason} inputFields{name description type{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{name}}}}}}}}} defaultValue} interfaces{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{name}}}}}}}}} enumValues(includeDeprecated:true){name description isDeprecated deprecationReason} possibleTypes{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{name}}}}}}}}}} directives{name description locations args{name description type{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{kind name ofType{name}}}}}}}}} defaultValue}}}}"""

  val introspectSangria = {
    val query = QueryParser.parse(introspectionStringFromCaliban).get
    for {
      introspectionResponseRaw <-
        ZIO
          .fromFuture { implicit ec =>
            Executor
              .execute(
                Sangria.schema,
                query // Or sangria.introspection.introspectionQuery
              )
          }
      introspectionResponse = introspectionResponseRaw.hcursor
        .downField("data")
        .focus
        .get
      sdl = Sangria.schema.renderPretty
      _ <- ZIO.attempt(Files.write(Path.of("sdl.graphql"), sdl.getBytes()))
      _ <- ZIO.attempt(
        Files.write(
          Path.of("introspection-response.graphql"),
          introspectionResponse.spaces2.getBytes()
        )
      )
      schema <- SchemaLoader
        .fromString(introspectionResponse.spaces2)
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

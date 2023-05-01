package example

import caliban.GraphQL
import caliban.GraphQL.graphQL
import caliban.ResponseValue
import caliban.RootResolver
import caliban.execution.Field
import caliban.tools.RemoteSchema
import caliban.tools.SchemaLoader
import caliban.tools.stitching.RemoteQuery
import caliban.tools.stitching.RemoteResolver
import caliban.tools.stitching.RemoteSchemaResolver
import zio.URIO
import zio.ZIO
import caliban.schema.Schema.auto._

object Caliban {

  final case class StatusOkOutput(output: Boolean)

  final case class StatusQueries(
      ok: () => URIO[Any, StatusOkOutput]
  )

  final case class Queries(
      status: StatusQueries
  )

  val schema: GraphQL[Any] =
    graphQL(
      RootResolver(
        Queries(
          StatusQueries { () =>
            ZIO.die(new RuntimeException("Oh no caliban dies"))
          }
        )
      )
    )

}

package example

import zio.EnvironmentTag
import zio.Scope
import zio.ZIO
import zio.ZIOApp
import zio.ZIOAppArgs
import zio.ZIOAppDefault
import zio.ZLayer
import sttp.model.Uri
import sttp.client3.SttpBackend
import sttp.client3.Identity
import sttp.client3.testing.SttpBackendStub
import sttp.client3.httpclient.zio.HttpClientZioBackend
import caliban.tools.RemoteSchema
import zio.FiberRef

object Hello extends ZIOAppDefault {

  val query = """
     query queryTwoGraphs {
       status {
         ok {
           output
         }
       }
       sangriaStatus {
        output
        userName
       }
     }
  """

  val mutation = """
     mutation testMutation {
       sangriaMutation(add: 5) {
        result
       }
     }
  """

  override def run = {
    val requestInit: ZLayer[Any, Nothing, Gateway.RequestContext] =
      ZLayer.scoped(
        FiberRef.make(Option.empty[Gateway.Token])
      ) ++ ZLayer.scoped(
        FiberRef.make(Option.empty[Gateway.ClientIp])
      ) ++ ZLayer.scoped(
        FiberRef.make(Option.empty[Gateway.UserId])
      )

    for {
      graph <- Gateway.graph
      interpreter <- graph.interpreter.orDie
      _ <- interpreter.check(query)
      _ <- interpreter.check(mutation)
      queryResult <- interpreter.execute(query).provide(requestInit)
      mutationResult <- interpreter
        .execute(mutation)
        .provide(requestInit)
      _ <- zio.Console.printLine("--- Composed graph SDL ---")
      _ <- zio.Console.printLine(graph.render)
      _ <- zio.Console.printLine("--- Query result ---")
      _ <- zio.Console.printLine(queryResult)
      _ <- zio.Console.printLine("--- Mutation result ---")
      _ <- zio.Console.printLine(mutationResult)
    } yield ()
  }

}

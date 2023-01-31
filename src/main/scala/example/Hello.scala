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
       }
     }
  """

  override def run = {
    for {
      // sttpBackend <- HttpClientZioBackend()
      // response <- caliban.tools.IntrospectionClient
      //   .introspect(
      //     "http://...",
      //     None
      //   )
      //   .provide(ZLayer.succeed(sttpBackend))
      // schema = RemoteSchema.parseRemoteSchema(response)
      // _ <- zio.Console.printLine(s"Response: $response")
      // _ <- zio.Console.printLine("------- DIVIDER ------")
      // _ <- zio.Console.printLine(s"Schme: $schema")
      interpreter <- Gateway.interpreter
      _ <- interpreter.check(query)
    } yield ()
  }

}

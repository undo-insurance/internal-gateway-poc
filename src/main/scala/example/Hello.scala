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
      graph <- Gateway.graph
      interpreter <- graph.interpreter.orDie
      _ <- interpreter.check(query)
      result <- interpreter.execute(query)
      _ <- zio.Console.printLine("Composed graph SDL")
      _ <- zio.Console.printLine(graph.render)
      _ <- zio.Console.printLine(result)
    } yield ()
  }

}

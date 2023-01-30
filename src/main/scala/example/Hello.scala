package example

import zio.EnvironmentTag
import zio.Scope
import zio.ZIO
import zio.ZIOApp
import zio.ZIOAppArgs
import zio.ZIOAppDefault
import zio.ZLayer

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
      interpreter <- Gateway.interpreter
      _ <- interpreter.check(query)
    } yield ()
  }

}

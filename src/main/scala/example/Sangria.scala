package example

import io.circe._
import io.circe.syntax._
import sangria.execution.Executor
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.parser.QueryParser
import sangria.schema.ObjectType
import sangria.schema._

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Sangria {

  case class StatusOkOutput(output: Boolean, userName: String)

  implicit val StatusType = deriveObjectType[UserContext, StatusOkOutput]()

  val queryType = ObjectType(
    "Query",
    fields[UserContext, Unit](
      Field(
        "sangriaStatus",
        StatusType,
        description = None,
        arguments = Nil,
        resolve = ctx => StatusOkOutput(output = false, ctx.ctx.name)
      ),
      Field(
        "sangriaError",
        IntType,
        description = None,
        arguments = Nil,
        resolve = ctx => throw new RuntimeException("oh no")
      )
    )
  )

  case class MutationResult(result: Int)
  implicit val MutationResultType = deriveObjectType[Unit, MutationResult]()

  val mutationArg = Argument("add", IntType)
  val mutationType = ObjectType(
    "Mutation",
    fields[Unit, Unit](
      Field(
        name = "sangriaMutation",
        description = None,
        arguments = mutationArg :: Nil,
        fieldType = MutationResultType,
        resolve = ctx => {
          val toAdd = ctx.arg(mutationArg)
          MutationResult(result = toAdd + 1)
        }
      )
    )
  )

  val schema: Schema[UserContext, Unit] =
    Schema(query = queryType, mutation = Some(mutationType))

  def handleRequest(
      body: JsonObject,
      userContext: UserContext
  ): Future[Try[Json]] = {
    implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
    val query = body("query").flatMap(_.asString).getOrElse("")
    val operation = body("operationName").flatMap(_.asString)
    val vars = body("variables") match {
      case Some(o) if o.isObject => o
      case _                     => Json.obj()
    }

    QueryParser.parse(query) match {
      // query parsed successfully, time to execute it!
      case Success(queryAst) =>
        for {
          ex <- {
            Executor
              .execute(
                schema,
                queryAst,
                variables = vars,
                operationName = operation,
                userContext = userContext
              )
              .map(Success.apply)
              .recover { case error => Failure(error) }
          }
        } yield ex
      // can't parse GraphQL query, return error
      case Failure(error) =>
        Future.successful(
          Failure(new RuntimeException("Could not parse the request"))
        )
    }
  }

}

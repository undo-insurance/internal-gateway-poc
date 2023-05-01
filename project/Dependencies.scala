import sbt._

object Dependencies {

  object Versions {
    val caliban =
      "2.0.2+34-04bb5f78-SNAPSHOT"
    val sangria = "3.5.0"
    val zio = "2.0.5"
    val circe = "0.14.3"
    val sangriaCirce = "1.3.2"
    val sangriaMarshalling = "1.0.8"
    val akkaHttp = "10.2.10"
    val akkaHttpCirce = "1.39.2"
    val tapir = "1.2.9"
    val zioTestAkkaHttp = "2.0.2"
  }

  lazy val calibanTools =
    "com.github.ghostdogpr" %% "caliban-tools" % Versions.caliban
  lazy val caliban = "com.github.ghostdogpr" %% "caliban" % Versions.caliban
  lazy val sangria = "org.sangria-graphql" %% "sangria" % Versions.sangria
  lazy val sangriaCirce =
    "org.sangria-graphql" %% "sangria-circe" % Versions.sangriaCirce
  lazy val sangriaMarshalling =
    "org.sangria-graphql" %% "sangria-marshalling-api" % Versions.sangriaMarshalling
  lazy val zio = "dev.zio" %% "zio" % Versions.zio
  lazy val circe = "io.circe" %% "circe-core" % Versions.circe
  lazy val circeParser = "io.circe" %% "circe-parser" % Versions.circe
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp
  lazy val calibanAkkaHttp =
    "com.github.ghostdogpr" %% "caliban-akka-http" % Versions.caliban
  lazy val akkaHttpCirce =
    "de.heikoseeberger" %% "akka-http-circe" % Versions.akkaHttpCirce
  lazy val calibanTapir =
    "com.github.ghostdogpr" %% "caliban-tapir" % Versions.caliban
  lazy val tapirCirce =
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % Versions.tapir
  lazy val zioTest = "dev.zio" %% "zio-test" % Versions.zio % Test
  lazy val zioTestAkkaHttp =
    "info.senia" %% "zio-test-akka-http" % Versions.zioTestAkkaHttp % Test
}

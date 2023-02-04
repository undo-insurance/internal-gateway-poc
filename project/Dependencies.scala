import sbt._

object Dependencies {

  object Versions {
    val caliban =
      "2.0.2+31-9553ad8f+20230204-2022-SNAPSHOT" // This change is needed https://github.com/ghostdogpr/caliban/pull/1596
    val sangria = "3.5.0"
    val zio = "2.0.5"
    val circe = "0.14.3"
    val sangriaCirce = "1.3.2"
    val sangriaMarshalling = "1.0.8"
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
}

import Dependencies._

ThisBuild / scalaVersion := "2.13.8"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "internal-gateway-poc",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= List(
      caliban,
      calibanTools,
      sangria,
      sangriaCirce,
      sangriaMarshalling,
      zio,
      circe,
      circeParser
    )
  )

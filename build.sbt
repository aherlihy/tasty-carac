ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

val sparkVersion = "3.3.1"
lazy val root = (project in file("."))
  .settings(
    name := "TASTyCarac",
    libraryDependencies += "ch.epfl.scala" %% "tasty-query" % "0.7.3"
  )

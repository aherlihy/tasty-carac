ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation"
)

lazy val examples = (project in file("examples"))
 .settings(
   name := "Examples to analyze",
 )

val runExample = inputKey[Unit]("Run with the examples on the classpath")

val sparkVersion = "3.3.1"
lazy val root = (project in file("."))
  .settings(
    name := "TASTyCarac",
    libraryDependencies += "ch.epfl.scala" %% "tasty-query" % "0.7.3",
    libraryDependencies += "org.scala-lang" %% "scala3-compiler" % "3.2.1",
    libraryDependencies += ("io.get-coursier" %% "coursier" % "2.1.3").cross(CrossVersion.for3Use2_13),
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,

    runExample := Def.inputTaskDyn {
      val main = "tastycarac.Main"
      val extraArgs = Def.spaceDelimited("<arg>").parsed
      val classpathEntry = (examples / Compile / classDirectory).value.getAbsolutePath

      val combined = Seq(main) ++ extraArgs ++ Seq(classpathEntry)
      (Compile / runMain).toTask(combined.mkString(" ", " ", ""))
    }.dependsOn(examples / Compile / compile)
     .evaluated,
  )

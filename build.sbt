name := """Tardar Sauce"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
    jdbc,
    cache,
    "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
    ws
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ )

libraryDependencies ++= Seq(
    specs2, // mostly for WithServer
    "org.scalatestplus" %% "play" % "1.4.0" % "test",
    "org.mockito" % "mockito-core" % "1.9.5"
)

routesGenerator := InjectedRoutesGenerator

javaOptions in Test += "-Dlogger.file=conf/test-logger.xml"

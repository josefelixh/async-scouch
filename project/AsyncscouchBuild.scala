import sbt._
import sbt.Keys._

object AsyncscouchBuild extends Build {

  val (projectOrganization, projectName, projectVersion) = ("org.josefelixh", "async-scouch", "0.1")

  val repos = Seq(
    "typesafe-releases" at "http://repo.typesafe.com/typesafe/maven-releases"
  )

  val appDependencies = Seq(
    "play" %% "play-iteratees" % "2.1.1",
    "com.ning" % "async-http-client" % "1.7.14"
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "1.9.1" % "test",
    "org.mockito" % "mockito-core" % "1.9.5" % "test"
  )

  lazy val asyncscouch = Project(
    id = "async-scouch",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := projectName,
      organization := projectOrganization,
      version := projectVersion,
      scalaVersion := "2.10.1",
      scalacOptions := Seq("-feature", "-language:implicitConversions"),
      libraryDependencies ++= appDependencies ++ testDependencies,
      resolvers ++= repos
    )
  )
}

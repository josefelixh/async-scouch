import sbt._
import sbt.Keys._

object AsyncscouchBuild extends Build {

  val (projectOrganization, projectName, projectVersion) = ("org.josefelixh", "async-scouch", "0.1")

  val mandubianRepos = Seq(
    "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
    "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
  )

  val repos = Seq(
    "typesafe-releases" at "http://repo.typesafe.com/typesafe/maven-releases"
  ) ++ mandubianRepos

  val appDependencies = Seq(
    //"play" %% "play-iteratees" % "2.1.1",
    "play" %% "play-json" % "2.2-SNAPSHOT",
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

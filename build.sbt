name := """knolx-portal"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalastyle" %% "scalastyle" % "0.8.0",
  "com.github.t3hnar" %% "scala-bcrypt" % "3.0",
  "net.codingwell" %% "scala-guice" % "4.0.0",
  "org.reactivemongo" %% "reactivemongo-play-json" % "0.12.3",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.3",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  specs2 % Test
)


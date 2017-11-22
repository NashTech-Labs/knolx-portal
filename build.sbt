name := """knolx-portal"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  ehcache,
  filters,
  ws,
  guice,
  "org.scalastyle" %% "scalastyle" % "0.8.0",
  "com.github.t3hnar" %% "scala-bcrypt" % "3.0",
  "net.codingwell" %% "scala-guice" % "4.1.0",
  "org.reactivemongo" %% "reactivemongo-play-json" % "0.12.3",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.5-play26",
  "com.github.simplyscala" %% "scalatest-embedmongo" % "0.2.4",
  "com.typesafe.play" %% "play-mailer" % "5.0.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.2" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  specs2 % Test
)

javaOptions in Test += "-Dconfig.file=conf/test.conf"

coverageExcludedPackages := "<empty>;Reverse.*;router.Routes.*;controllers.JavascriptRouter;"

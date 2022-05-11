ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "parallel"
  )

projectDependencies ++= Seq(
    "org.eclipse.jetty" % s"jetty-servlets" % "9.4.34.v20201102",
    "org.eclipse.jetty" % s"jetty-webapp" % "9.4.34.v20201102",
    "org.eclipse.jetty" % s"jetty-util" % "9.4.34.v20201102",
    "org.apache.httpcomponents" % "httpclient" % "4.5.13",
    "org.scalatest" %% "scalatest" % "3.2.9" % Test,
    "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.0",
    "org.typelevel" %% "cats-effect-kernel" % "3.3.11",
    "org.typelevel" %% "cats-effect-std"    % "3.3.11",
    "org.typelevel" %% "cats-effect"        % "3.3.11",

)
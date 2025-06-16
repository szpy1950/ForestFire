ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "ForestFireSimulation",
    organization := "ch.youruni.funcprog",

    // Scala 3 compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    ),

    // Dependencies for testing (optional)
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),

    // Main class specification
    Compile / mainClass := Some("ForestFire.SimpleForestFireSimulation"),

    // Run configuration
    run / fork := true,
    run / javaOptions ++= Seq("-Xmx2G", "-Xms1G")
  )
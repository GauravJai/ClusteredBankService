ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"

import sbt.Keys.libraryDependencies
import sbt.CrossVersion

// Akka versions compatible with Scala 2.13.12
val akkaVersion = "2.8.8"
val akkaHttpVersion = "10.5.3"

val logbackVersion = "1.5.32"

lazy val root = (project in file("."))
  .settings(
    name := "ClusteredBankService",
    libraryDependencies ++= Seq(
      // Core
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,

      // HTTP
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

      // Persistence
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      
      // In-memory persistence for development
      "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion,

      // Clustering & sharding
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,

      // Testing
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,

      // Logging
      "ch.qos.logback" % "logback-classic" % logbackVersion
    )
  )

package com.clusteredbankservice.config

import com.typesafe.config.{Config, ConfigFactory}

trait TestConfig {
  val unitTestConfig: Config = ConfigFactory.parseString("""
    akka.actor.provider = "local"
    akka.remote.artery.enabled = off
    akka.cluster.enabled = off
    akka.actor.allow-java-serialization = on
    akka.http.server.port = 0
    akka.loglevel = "WARNING"
  """)

  val integrationTestConfig: Config = ConfigFactory.load("application-test")
}
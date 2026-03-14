package com.clusteredbankservice

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.cluster.typed.{Cluster, JoinSeedNodes}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.clusteredbankservice.http.BankAccountRoutes
import com.clusteredbankservice.sharding.BankAccountSharding
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    
    val system = ActorSystem[SpawnProtocol.Command](
      Behaviors.setup[SpawnProtocol.Command] { context =>
        implicit val system: ActorSystem[Nothing] = context.system
        implicit val ec: ExecutionContext = system.executionContext
        
        // Initialize cluster
        val cluster = Cluster(system)
        val seedNodes = List(
          cluster.selfMember.address
        )
        cluster.manager ! JoinSeedNodes(seedNodes)
        
        // Initialize sharding guardian
        val shardingGuardian = system.systemActorOf(BankAccountSharding(), "shardingGuardian")
        
        // Initialize sharding helper
        val sharding = BankAccountSharding.ShardingHelper(
          akka.cluster.sharding.typed.scaladsl.ClusterSharding(system)
        )
        
        // Initialize HTTP routes
        val routes: Route = BankAccountRoutes(sharding)(system, system.executionContext).routes
        
        // Start HTTP server
        val bindingFuture: Future[Http.ServerBinding] = 
          Http().newServerAt("0.0.0.0", 8080).bind(routes)
        
        bindingFuture.onComplete {
          case Success(binding) =>
            val address = binding.localAddress
            system.log.info(s"Clustered Bank Service server online at http://${address.getHostString}:${address.getPort}/")
            system.log.info(s"Cluster self address: ${cluster.selfMember.address}")
            
            // Add shutdown hook
            sys.addShutdownHook {
              binding.unbind()
              system.terminate()
            }
            
          case Failure(exception) =>
            system.log.error(s"Failed to bind HTTP server: ${exception.getMessage}")
            system.terminate()
        }
        
        Behaviors.empty
      },
      "ClusteredBankServiceSystem",
      config
    )
  }
}

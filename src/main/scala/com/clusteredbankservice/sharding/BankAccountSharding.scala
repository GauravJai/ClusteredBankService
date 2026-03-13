package com.clusteredbankservice.sharding

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import com.clusteredbankservice.actor.BankAccountActor
import com.clusteredbankservice.domain.CommandResponse

object BankAccountSharding {
  
  // Entity type key for Bank Account actors
  val BankAccountEntityKey: EntityTypeKey[BankAccountActor.Command] = 
    EntityTypeKey[BankAccountActor.Command]("BankAccount")

  // Message extractor for sharding
  object BankAccountMessageExtractor extends ShardingMessageExtractor[BankAccountActor.Command, BankAccountActor.Command] {
    override def entityId(message: BankAccountActor.Command): String = message match {
      case BankAccountActor.CreateAccountCmd(accountId, _, _, _) => accountId
      case BankAccountActor.DepositMoneyCmd(accountId, _, _) => accountId
      case BankAccountActor.WithdrawMoneyCmd(accountId, _, _) => accountId
      case BankAccountActor.GetBalanceCmd(accountId, _) => accountId
      case BankAccountActor.GetAccountDetailsCmd(accountId, _) => accountId
      case BankAccountActor.CloseAccountCmd(accountId, _) => accountId
    }

    override def shardId(entityId: String): String = {
      // Simple shard allocation - you can use more sophisticated logic if needed
      // This distributes entities across shards based on hash of entityId
      (Math.abs(entityId.hashCode) % 10).toString
    }

    override def unwrapMessage(message: BankAccountActor.Command): BankAccountActor.Command = message
  }

  // Sharding guardian behavior
  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    implicit val system: ActorSystem[Nothing] = context.system
    val sharding = ClusterSharding(system)

    // Setup the Bank Account entity type
    val bankAccountEntityType = Entity(BankAccountEntityKey) { entityContext =>
      BankAccountActor(entityContext.entityId)
    }

    // Initialize the cluster sharding
    sharding.init(bankAccountEntityType)

    context.log.info("Bank Account Cluster Sharding initialized")

    Behaviors.empty
  }

  sealed trait NotUsed
  case object NotUsed extends NotUsed

  // Helper methods to interact with sharded entities
  class ShardingHelper(sharding: akka.cluster.sharding.typed.scaladsl.ClusterSharding) {
    
    def getAccountEntity(accountId: String): akka.cluster.sharding.typed.scaladsl.EntityRef[BankAccountActor.Command] = {
      sharding.entityRefFor(BankAccountEntityKey, accountId)
    }

    def createAccount(
      accountId: String, 
      initialBalance: Double, 
      owner: String,
      replyTo: ActorRef[CommandResponse]
    ): Unit = {
      val entityRef = getAccountEntity(accountId)
      entityRef ! BankAccountActor.CreateAccountCmd(accountId, initialBalance, owner, replyTo)
    }

    def depositMoney(
      accountId: String,
      amount: Double,
      replyTo: ActorRef[CommandResponse]
    ): Unit = {
      val entityRef = getAccountEntity(accountId)
      entityRef ! BankAccountActor.DepositMoneyCmd(accountId, amount, replyTo)
    }

    def withdrawMoney(
      accountId: String,
      amount: Double,
      replyTo: ActorRef[CommandResponse]
    ): Unit = {
      val entityRef = getAccountEntity(accountId)
      entityRef ! BankAccountActor.WithdrawMoneyCmd(accountId, amount, replyTo)
    }

    def getBalance(
      accountId: String,
      replyTo: ActorRef[CommandResponse]
    ): Unit = {
      val entityRef = getAccountEntity(accountId)
      entityRef ! BankAccountActor.GetBalanceCmd(accountId, replyTo)
    }

    def getAccountDetails(
      accountId: String,
      replyTo: ActorRef[CommandResponse]
    ): Unit = {
      val entityRef = getAccountEntity(accountId)
      entityRef ! BankAccountActor.GetAccountDetailsCmd(accountId, replyTo)
    }

    def closeAccount(
      accountId: String,
      replyTo: ActorRef[CommandResponse]
    ): Unit = {
      val entityRef = getAccountEntity(accountId)
      entityRef ! BankAccountActor.CloseAccountCmd(accountId, replyTo)
    }
  }

  object ShardingHelper {
    def apply(sharding: akka.cluster.sharding.typed.scaladsl.ClusterSharding): ShardingHelper = new ShardingHelper(sharding)
  }
}

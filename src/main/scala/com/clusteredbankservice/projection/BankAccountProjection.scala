package com.clusteredbankservice.projection

import akka.actor.typed.ActorSystem
import com.clusteredbankservice.actor.BankAccountActor

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object BankAccountProjection {
  
  // In-memory storage for demonstration (in production, use a real database)
  case class ReadModelStorage(
    accounts: Map[String, AccountReadModel] = Map.empty,
    transactions: List[TransactionRecord] = List.empty,
    statistics: AccountStatistics = AccountStatistics.empty
  ) {
    def updateAccount(event: BankAccountActor.Event): ReadModelStorage = {
      event match {
        case BankAccountActor.AccountCreatedEvt(accountId, initialBalance, owner, timestamp) =>
          val newAccount = AccountReadModel(
            accountId = accountId,
            balance = initialBalance,
            owner = owner,
            isActive = true,
            lastUpdated = timestamp,
            totalDeposits = if (initialBalance > 0) initialBalance else 0.0,
            totalWithdrawals = 0.0,
            transactionCount = if (initialBalance > 0) 1 else 0
          )
          val transaction = TransactionRecord(
            transactionId = UUID.randomUUID().toString,
            accountId = accountId,
            transactionType = "CREATION",
            amount = initialBalance,
            balanceAfter = initialBalance,
            timestamp = timestamp,
            description = s"Account created with initial balance: $initialBalance"
          )
          copy(
            accounts = accounts + (accountId -> newAccount),
            transactions = transaction :: transactions,
            statistics = statistics.copy(
              totalAccounts = statistics.totalAccounts + 1,
              activeAccounts = statistics.activeAccounts + 1,
              totalBalance = statistics.totalBalance + initialBalance,
              averageBalance = if (statistics.totalAccounts + 1 > 0) 
                (statistics.totalBalance + initialBalance) / (statistics.totalAccounts + 1) else 0.0,
              totalTransactions = statistics.totalTransactions + 1,
              lastUpdated = timestamp
            )
          )
          
        case BankAccountActor.MoneyDepositedEvt(accountId, amount, newBalance, timestamp) =>
          val updatedAccount = accounts.get(accountId).map { account =>
            account.copy(
              balance = newBalance,
              lastUpdated = timestamp,
              totalDeposits = account.totalDeposits + amount,
              transactionCount = account.transactionCount + 1
            )
          }.getOrElse(AccountReadModel.empty(accountId).copy(
            balance = newBalance,
            lastUpdated = timestamp,
            totalDeposits = amount,
            transactionCount = 1
          ))
          
          val transaction = TransactionRecord(
            transactionId = UUID.randomUUID().toString,
            accountId = accountId,
            transactionType = "DEPOSIT",
            amount = amount,
            balanceAfter = newBalance,
            timestamp = timestamp,
            description = s"Deposit: $amount"
          )
          
          val oldBalance = accounts.get(accountId).map(_.balance).getOrElse(0.0)
          copy(
            accounts = accounts + (accountId -> updatedAccount),
            transactions = transaction :: transactions,
            statistics = statistics.copy(
              totalBalance = statistics.totalBalance + (newBalance - oldBalance),
              averageBalance = if (statistics.totalAccounts > 0) 
                (statistics.totalBalance + (newBalance - oldBalance)) / statistics.totalAccounts else 0.0,
              totalTransactions = statistics.totalTransactions + 1,
              lastUpdated = timestamp
            )
          )
          
        case BankAccountActor.MoneyWithdrawnEvt(accountId, amount, newBalance, timestamp) =>
          val updatedAccount = accounts.get(accountId).map { account =>
            account.copy(
              balance = newBalance,
              lastUpdated = timestamp,
              totalWithdrawals = account.totalWithdrawals + amount,
              transactionCount = account.transactionCount + 1
            )
          }.getOrElse(AccountReadModel.empty(accountId).copy(
            balance = newBalance,
            lastUpdated = timestamp,
            totalWithdrawals = amount,
            transactionCount = 1
          ))
          
          val transaction = TransactionRecord(
            transactionId = UUID.randomUUID().toString,
            accountId = accountId,
            transactionType = "WITHDRAWAL",
            amount = amount,
            balanceAfter = newBalance,
            timestamp = timestamp,
            description = s"Withdrawal: $amount"
          )
          
          val oldBalance = accounts.get(accountId).map(_.balance).getOrElse(0.0)
          copy(
            accounts = accounts + (accountId -> updatedAccount),
            transactions = transaction :: transactions,
            statistics = statistics.copy(
              totalBalance = statistics.totalBalance + (newBalance - oldBalance),
              averageBalance = if (statistics.totalAccounts > 0) 
                (statistics.totalBalance + (newBalance - oldBalance)) / statistics.totalAccounts else 0.0,
              totalTransactions = statistics.totalTransactions + 1,
              lastUpdated = timestamp
            )
          )
          
        case BankAccountActor.AccountClosedEvt(accountId, finalBalance, timestamp) =>
          val updatedAccount = accounts.get(accountId).map { account =>
            account.copy(
              isActive = false,
              lastUpdated = timestamp,
              transactionCount = account.transactionCount + 1
            )
          }.getOrElse(AccountReadModel.empty(accountId).copy(
            isActive = false,
            lastUpdated = timestamp,
            transactionCount = 1
          ))
          
          val transaction = TransactionRecord(
            transactionId = UUID.randomUUID().toString,
            accountId = accountId,
            transactionType = "CLOSURE",
            amount = finalBalance,
            balanceAfter = finalBalance,
            timestamp = timestamp,
            description = s"Account closed with final balance: $finalBalance"
          )
          
          copy(
            accounts = accounts + (accountId -> updatedAccount),
            transactions = transaction :: transactions,
            statistics = statistics.copy(
              activeAccounts = if (accounts.get(accountId).exists(_.isActive)) 
                statistics.activeAccounts - 1 else statistics.activeAccounts,
              totalTransactions = statistics.totalTransactions + 1,
              lastUpdated = timestamp
            )
          )
      }
    }
  }

  // Handler for processing events
  class BankAccountEventHandler(implicit ec: ExecutionContext) {
    private var storage = ReadModelStorage()
    
    def handleEvent(event: BankAccountActor.Event): Future[ReadModelStorage] = {
      Future {
        storage = storage.updateAccount(event)
        storage
      }
    }
    
    def getAccount(accountId: String): Option[AccountReadModel] = storage.accounts.get(accountId)
    def getTransactions(accountId: String): List[TransactionRecord] = 
      storage.transactions.filter(_.accountId == accountId).sortBy(-_.timestamp.toEpochMilli)
    def getStatistics: AccountStatistics = storage.statistics
    def getAllAccounts: List[AccountReadModel] = storage.accounts.values.toList
  }

  // Projection setup - simplified for now
  def initProjection(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    
    // For now, just initialize the handler without actual projection
    // In production, you would set up the full projection here
    val projectionHandler = new BankAccountEventHandler()
    
    system.log.info("Bank Account Projection initialized (simplified version)")
  }
}

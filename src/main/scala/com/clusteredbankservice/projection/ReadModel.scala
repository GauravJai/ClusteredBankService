package com.clusteredbankservice.projection

import java.time.Instant

// Read model for optimized queries
case class AccountReadModel(
  accountId: String,
  balance: Double,
  owner: String,
  isActive: Boolean,
  lastUpdated: Instant,
  totalDeposits: Double,
  totalWithdrawals: Double,
  transactionCount: Int
)

object AccountReadModel {
  def empty(accountId: String): AccountReadModel = 
    AccountReadModel(accountId, 0.0, "", isActive = false, Instant.EPOCH, 0.0, 0.0, 0)
}

// Transaction history for reporting
case class TransactionRecord(
  transactionId: String,
  accountId: String,
  transactionType: String, // "DEPOSIT", "WITHDRAWAL", "CREATION", "CLOSURE"
  amount: Double,
  balanceAfter: Double,
  timestamp: Instant,
  description: String
)

// Aggregated statistics
case class AccountStatistics(
  totalAccounts: Int,
  activeAccounts: Int,
  totalBalance: Double,
  averageBalance: Double,
  totalTransactions: Int,
  lastUpdated: Instant
)

object AccountStatistics {
  def empty: AccountStatistics = 
    AccountStatistics(0, 0, 0.0, 0.0, 0, Instant.EPOCH)
}

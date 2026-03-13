package com.clusteredbankservice.domain

import java.time.Instant

// Command Responses
sealed trait CommandResponse
case class CommandSuccess(message: String) extends CommandResponse
case class CommandFailure(reason: String) extends CommandResponse
case class BalanceResponse(accountId: String, balance: Double) extends CommandResponse
case class AccountDetailsResponse(state: BankAccountState) extends CommandResponse

// Domain State
case class BankAccountState(
  accountId: String,
  balance: Double,
  owner: String,
  isActive: Boolean,
  lastUpdated: Instant
)

object BankAccountState {
  def empty(accountId: String): BankAccountState = 
    BankAccountState(accountId, 0.0, "", isActive = false, Instant.EPOCH)
}

// Validation Errors
sealed trait ValidationError extends Throwable
case class InsufficientFunds(currentBalance: Double, requestedAmount: Double) extends ValidationError
case class AccountAlreadyExists(accountId: String) extends ValidationError
case class AccountNotFound(accountId: String) extends ValidationError
case class AccountAlreadyClosed(accountId: String) extends ValidationError
case class InvalidAmount(amount: Double) extends ValidationError

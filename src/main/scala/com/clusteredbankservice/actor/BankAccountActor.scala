package com.clusteredbankservice.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.clusteredbankservice.domain._

object BankAccountActor {
  
  def apply(accountId: String): Behavior[Command] = {
    EventSourcedBehavior[Command, Event, BankAccountState](
      persistenceId = PersistenceId.ofUniqueId(s"bank-account-$accountId"),
      emptyState = BankAccountState.empty(accountId),
      commandHandler = (state, command) => handleCommand(state, command),
      eventHandler = (state, event) => handleEvent(state, event)
    )
  }

  // Command Protocol
  sealed trait Command {
    def replyTo: ActorRef[CommandResponse]
  }
  
  case class CreateAccountCmd(accountId: String, initialBalance: Double, owner: String, replyTo: ActorRef[CommandResponse]) extends Command
  case class DepositMoneyCmd(accountId: String, amount: Double, replyTo: ActorRef[CommandResponse]) extends Command
  case class WithdrawMoneyCmd(accountId: String, amount: Double, replyTo: ActorRef[CommandResponse]) extends Command
  case class GetBalanceCmd(accountId: String, replyTo: ActorRef[CommandResponse]) extends Command
  case class GetAccountDetailsCmd(accountId: String, replyTo: ActorRef[CommandResponse]) extends Command
  case class CloseAccountCmd(accountId: String, replyTo: ActorRef[CommandResponse]) extends Command

  // Event Protocol
  sealed trait Event {
    def accountId: String
    def timestamp: java.time.Instant
  }
  
  case class AccountCreatedEvt(accountId: String, initialBalance: Double, owner: String, timestamp: java.time.Instant) extends Event
  case class MoneyDepositedEvt(accountId: String, amount: Double, newBalance: Double, timestamp: java.time.Instant) extends Event
  case class MoneyWithdrawnEvt(accountId: String, amount: Double, newBalance: Double, timestamp: java.time.Instant) extends Event
  case class AccountClosedEvt(accountId: String, finalBalance: Double, timestamp: java.time.Instant) extends Event

  private def handleCommand(state: BankAccountState, command: Command): Effect[Event, BankAccountState] = {
    command match {
      case CreateAccountCmd(accountId, initialBalance, owner, replyTo) =>
        if (state.isActive) {
          Effect.reply(replyTo)(CommandFailure(s"Account ${state.accountId} already exists"))
        } else if (initialBalance < 0) {
          Effect.reply(replyTo)(CommandFailure("Initial balance cannot be negative"))
        } else {
          val event = AccountCreatedEvt(state.accountId, initialBalance, owner, java.time.Instant.now())
          Effect.persist(event).thenReply(replyTo)(_ => CommandSuccess(s"Account ${state.accountId} created successfully"))
        }

      case DepositMoneyCmd(accountId, amount, replyTo) =>
        if (!state.isActive) {
          Effect.reply(replyTo)(CommandFailure(s"Account ${state.accountId} is not active"))
        } else if (amount <= 0) {
          Effect.reply(replyTo)(CommandFailure("Deposit amount must be positive"))
        } else {
          val newBalance = state.balance + amount
          val event = MoneyDepositedEvt(state.accountId, amount, newBalance, java.time.Instant.now())
          Effect.persist(event).thenReply(replyTo)(_ => CommandSuccess(s"Deposited $amount to account ${state.accountId}"))
        }

      case WithdrawMoneyCmd(accountId, amount, replyTo) =>
        if (!state.isActive) {
          Effect.reply(replyTo)(CommandFailure(s"Account ${state.accountId} is not active"))
        } else if (amount <= 0) {
          Effect.reply(replyTo)(CommandFailure("Withdrawal amount must be positive"))
        } else if (state.balance < amount) {
          Effect.reply(replyTo)(CommandFailure(s"Insufficient funds. Current balance: ${state.balance}, requested: $amount"))
        } else {
          val newBalance = state.balance - amount
          val event = MoneyWithdrawnEvt(state.accountId, amount, newBalance, java.time.Instant.now())
          Effect.persist(event).thenReply(replyTo)(_ => CommandSuccess(s"Withdrew $amount from account ${state.accountId}"))
        }

      case GetBalanceCmd(accountId, replyTo) =>
        if (!state.isActive) {
          Effect.reply(replyTo)(CommandFailure(s"Account ${state.accountId} is not active"))
        } else {
          Effect.reply(replyTo)(BalanceResponse(state.accountId, state.balance))
        }

      case GetAccountDetailsCmd(accountId, replyTo) =>
        Effect.reply(replyTo)(AccountDetailsResponse(state))

      case CloseAccountCmd(accountId, replyTo) =>
        if (!state.isActive) {
          Effect.reply(replyTo)(CommandFailure(s"Account ${state.accountId} is already closed"))
        } else {
          val event = AccountClosedEvt(state.accountId, state.balance, java.time.Instant.now())
          Effect.persist(event).thenReply(replyTo)(_ => CommandSuccess(s"Account ${state.accountId} closed successfully"))
        }
    }
  }

  private def handleEvent(state: BankAccountState, event: Event): BankAccountState = {
    event match {
      case AccountCreatedEvt(accountId, initialBalance, owner, timestamp) =>
        BankAccountState(accountId, initialBalance, owner, isActive = true, timestamp)
      
      case MoneyDepositedEvt(accountId, amount, newBalance, timestamp) =>
        state.copy(balance = newBalance, lastUpdated = timestamp)
      
      case MoneyWithdrawnEvt(accountId, amount, newBalance, timestamp) =>
        state.copy(balance = newBalance, lastUpdated = timestamp)
      
      case AccountClosedEvt(accountId, finalBalance, timestamp) =>
        state.copy(isActive = false, lastUpdated = timestamp)
    }
  }
}

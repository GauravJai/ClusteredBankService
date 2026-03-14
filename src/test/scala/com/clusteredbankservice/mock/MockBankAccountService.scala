package com.clusteredbankservice.mock

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.clusteredbankservice.actor.BankAccountActor
import com.clusteredbankservice.actor.BankAccountActor._
import com.clusteredbankservice.domain._

class MockBankAccountService(testKit: ActorTestKit) {
  def createMockBehavior(accountId: String): Behavior[BankAccountActor.Command] =
    Behaviors.receiveMessage[BankAccountActor.Command] {
      case CreateAccountCmd(id, bal, _, replyTo) =>
        if (bal < 0) replyTo ! CommandFailure("Initial balance cannot be negative")
        else replyTo ! CommandSuccess(s"Account $id created successfully")
        Behaviors.same

      case GetBalanceCmd(id, replyTo) =>
        if (id == "non-existent-account") replyTo ! CommandFailure(s"Account $id is not active")
        else replyTo ! BalanceResponse(id, 1000.0)
        Behaviors.same

      case GetAccountDetailsCmd(id, replyTo) =>
        replyTo ! AccountDetailsResponse(BankAccountState(id, 1000.0, "John Doe", true, java.time.Instant.now()))
        Behaviors.same

      case DepositMoneyCmd(id, amt, replyTo) =>
        if (amt <= 0) replyTo ! CommandFailure("Deposit amount must be positive")
        else replyTo ! CommandSuccess(s"Deposited $amt to account $id")
        Behaviors.same

      case WithdrawMoneyCmd(id, amt, replyTo) =>
        if (amt > 1000) replyTo ! CommandFailure("Insufficient funds")
        else replyTo ! CommandSuccess(s"Withdrew $amt from account $id")
        Behaviors.same

      case CloseAccountCmd(id, replyTo) =>
        replyTo ! CommandSuccess(s"Account $id closed successfully")
        Behaviors.same
    }

}
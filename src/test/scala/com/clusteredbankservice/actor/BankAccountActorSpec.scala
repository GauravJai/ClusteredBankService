package com.clusteredbankservice.actor

import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import BankAccountActor._
import com.clusteredbankservice.domain.{BankAccountState, CommandResponse}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class BankAccountActorSpec extends AnyWordSpec with Matchers {

  "BankAccountActor" should {

    "create an account successfully" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      val cmd = CreateAccountCmd(1000.0, "John Doe", replyTo.ref)
      testKit.run(cmd)
      
      replyTo.receiveAll() should contain(CommandSuccess("Account account-123 created successfully"))
      testKit.currentState shouldBe BankAccountState("account-123", 1000.0, "John Doe", isActive = true, testKit.currentState.lastUpdated)
    }

    "reject duplicate account creation" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account first time
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll() should contain(CommandSuccess("Account account-123 created successfully"))
      
      // Try to create same account again
      testKit.run(CreateAccountCmd(500.0, "Jane Doe", replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Account account-123 already exists"))
    }

    "deposit money successfully" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Deposit money
      testKit.run(DepositMoneyCmd(500.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandSuccess("Deposited 500.0 to account account-123"))
      
      testKit.currentState.balance shouldBe 1500.0
    }

    "reject deposit to non-existent account" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      testKit.run(DepositMoneyCmd(500.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Account account-123 is not active"))
    }

    "reject negative deposit amount" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Try to deposit negative amount
      testKit.run(DepositMoneyCmd(-100.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Deposit amount must be positive"))
    }

    "withdraw money successfully" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Withdraw money
      testKit.run(WithdrawMoneyCmd(300.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandSuccess("Withdrew 300.0 from account account-123"))
      
      testKit.currentState.balance shouldBe 700.0
    }

    "reject withdrawal with insufficient funds" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Try to withdraw more than balance
      testKit.run(WithdrawMoneyCmd(1500.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Insufficient funds. Current balance: 1000.0, requested: 1500.0"))
      
      testKit.currentState.balance shouldBe 1000.0
    }

    "reject negative withdrawal amount" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Try to withdraw negative amount
      testKit.run(WithdrawMoneyCmd(-100.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Withdrawal amount must be positive"))
    }

    "get balance successfully" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Get balance
      testKit.run(GetBalanceCmd(replyTo.ref))
      replyTo.receiveAll() should contain(BalanceResponse("account-123", 1000.0))
    }

    "get account details successfully" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Get account details
      testKit.run(GetAccountDetailsCmd(replyTo.ref))
      val responses = replyTo.receiveAll()
      responses should have size 1
      responses.head shouldBe a[AccountDetailsResponse]
      responses.head.asInstanceOf[AccountDetailsResponse].state.balance shouldBe 1000.0
    }

    "close account successfully" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      
      // Close account
      testKit.run(CloseAccountCmd(replyTo.ref))
      replyTo.receiveAll() should contain(CommandSuccess("Account account-123 closed successfully"))
      
      testKit.currentState.isActive shouldBe false
    }

    "reject operations on closed account" in {
      val testKit = BehaviorTestKit[Command](BankAccountActor("account-123"))
      val replyTo = TestInbox[CommandResponse]()
      
      // Create and close account
      testKit.run(CreateAccountCmd(1000.0, "John Doe", replyTo.ref))
      replyTo.receiveAll()
      testKit.run(CloseAccountCmd(replyTo.ref))
      replyTo.receiveAll()
      
      // Try to deposit to closed account
      testKit.run(DepositMoneyCmd(500.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Account account-123 is not active"))
      
      // Try to withdraw from closed account
      testKit.run(WithdrawMoneyCmd(300.0, replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Account account-123 is not active"))
      
      // Try to close already closed account
      testKit.run(CloseAccountCmd(replyTo.ref))
      replyTo.receiveAll() should contain(CommandFailure("Account account-123 is already closed"))
    }
  }
}

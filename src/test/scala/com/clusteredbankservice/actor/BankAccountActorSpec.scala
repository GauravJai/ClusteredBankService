package com.clusteredbankservice.actor

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorSystem
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import com.clusteredbankservice.actor.BankAccountActor._
import com.clusteredbankservice.config.TestConfig
import com.clusteredbankservice.domain._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

class BankAccountActorSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with TestConfig{

//  val testKit = ActorTestKit()
  val testKit = ActorTestKit(PersistenceTestKitPlugin.config.withFallback(unitTestConfig))
  implicit val typedSystem: ActorSystem[_] = testKit.system
  val persistenceTestKit = PersistenceTestKit(typedSystem)

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    // 2. Clear all journals and snapshots for every persistenceId
    persistenceTestKit.clearAll()
  }


  "BankAccountActor" should {

    "create an account successfully" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      val cmd = CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref)
      accountActor ! cmd

      replyTo.receiveMessage() shouldBe CommandSuccess("Account account-123 created successfully")

      val detailsProbe = TestProbe[CommandResponse]()
      accountActor ! GetAccountDetailsCmd("account-123", detailsProbe.ref)
      val detailsResponse = detailsProbe.receiveMessage().asInstanceOf[AccountDetailsResponse]
      detailsResponse.state shouldBe BankAccountState("account-123", 1000.0, "John Doe", isActive = true, detailsResponse.state.lastUpdated)
    }

    "reject duplicate account creation" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      
      // Create account first time
      val createProbe1 = TestProbe[CommandResponse]()
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", createProbe1.ref))
      createProbe1.receiveMessage() shouldBe CommandSuccess("Account account-123 created successfully")
      
      // Try to create same account again
      val createProbe2 = TestProbe[CommandResponse]()
      accountActor ! (CreateAccountCmd("account-123", 500.0, "Jane Doe", createProbe2.ref))
      createProbe2.receiveMessage() shouldBe CommandFailure("Account account-123 already exists")
    }

    "deposit money successfully" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Deposit money
      accountActor ! (DepositMoneyCmd("account-123", 500.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandSuccess("Deposited 500.0 to account account-123")
      
      val balanceInbox = TestProbe[CommandResponse]()
      accountActor ! (GetBalanceCmd("account-123", balanceInbox.ref))
      val balanceResponse = balanceInbox.receiveMessage().asInstanceOf[BalanceResponse]
      balanceResponse.balance shouldBe 1500.0
    }

    "reject deposit to non-existent account" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      accountActor ! (DepositMoneyCmd("account-123", 500.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandFailure("Account account-123 is not active")
    }

    "reject negative deposit amount" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Try to deposit negative amount
      accountActor ! (DepositMoneyCmd("account-123", -100.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandFailure("Deposit amount must be positive")
    }

    "withdraw money successfully" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Withdraw money
      accountActor ! (WithdrawMoneyCmd("account-123", 300.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandSuccess("Withdrew 300.0 from account account-123")
      
      val balanceInbox = TestProbe[CommandResponse]()
      accountActor ! (GetBalanceCmd("account-123", balanceInbox.ref))
      val balanceResponse = balanceInbox.receiveMessage().asInstanceOf[BalanceResponse]
      balanceResponse.balance shouldBe 700.0
    }

    "reject withdrawal with insufficient funds" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Try to withdraw more than balance
      accountActor ! (WithdrawMoneyCmd("account-123", 1500.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandFailure("Insufficient funds. Current balance: 1000.0, requested: 1500.0")
      
      val balanceInbox = TestProbe[CommandResponse]()
      accountActor ! (GetBalanceCmd("account-123", balanceInbox.ref))
      val balanceResponse = balanceInbox.receiveMessage().asInstanceOf[BalanceResponse]
      balanceResponse.balance shouldBe 1000.0
    }

    "reject negative withdrawal amount" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Try to withdraw negative amount
      accountActor ! (WithdrawMoneyCmd("account-123", -100.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandFailure("Withdrawal amount must be positive")
    }

    "get balance successfully" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Get balance
      accountActor ! (GetBalanceCmd("account-123", replyTo.ref))
      replyTo.receiveMessage() shouldBe BalanceResponse("account-123", 1000.0)
    }

    "get account details successfully" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Get account details
      accountActor ! (GetAccountDetailsCmd("account-123", replyTo.ref))
      val response = replyTo.receiveMessage()
      response shouldBe a[AccountDetailsResponse]
      response.asInstanceOf[AccountDetailsResponse].state.balance shouldBe 1000.0
    }

    "close account successfully" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      
      // Close account
      accountActor ! (CloseAccountCmd("account-123", replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandSuccess("Account account-123 closed successfully")
      
      val detailsInbox = TestProbe[CommandResponse]()
      accountActor ! (GetAccountDetailsCmd("account-123", detailsInbox.ref))
      val detailsResponse = detailsInbox.receiveMessage().asInstanceOf[AccountDetailsResponse]
      detailsResponse.state.isActive shouldBe false
    }

    "reject operations on closed account" in {
      val accountActor = testKit.spawn(BankAccountActor("account-123"))
      val replyTo = TestProbe[CommandResponse]()
      
      // Create and close account
      accountActor ! (CreateAccountCmd("account-123", 1000.0, "John Doe", replyTo.ref))
      replyTo.receiveMessage()
      accountActor ! (CloseAccountCmd("account-123", replyTo.ref))
      replyTo.receiveMessage()
      
      // Try to deposit to closed account
      accountActor ! (DepositMoneyCmd("account-123", 500.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandFailure("Account account-123 is not active")
      
      // Try to withdraw from closed account
      accountActor ! (WithdrawMoneyCmd("account-123", 300.0, replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandFailure("Account account-123 is not active")
      
      // Try to close already closed account
      accountActor ! (CloseAccountCmd("account-123", replyTo.ref))
      replyTo.receiveMessage() shouldBe CommandFailure("Account account-123 is already closed")
    }
  }
}

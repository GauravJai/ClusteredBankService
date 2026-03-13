package com.clusteredbankservice.http

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.clusteredbankservice.actor.BankAccountActor
import com.clusteredbankservice.domain.{BankAccountState, CommandResponse}
import com.clusteredbankservice.sharding.BankAccountSharding.ShardingHelper
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class BankAccountRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with JsonFormats {
  
  implicit val timeout: Timeout = 30.seconds
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "TestSystem")
  implicit val ec: ExecutionContext = system.executionContext

  // Mock ShardingHelper
  class MockShardingHelper extends ShardingHelper(null) {
    override def getAccountEntity(accountId: String): akka.actor.typed.ActorRef[BankAccountActor.Command] = 
      new akka.actor.typed.ActorRef[BankAccountActor.Command] {
        override def tell(msg: BankAccountActor.Command): Unit = msg match {
          case BankAccountActor.CreateAccountCmd(initialBalance, owner, replyTo) =>
            if (accountId == "existing-account") {
              replyTo ! CommandFailure(s"Account $accountId already exists")
            } else if (initialBalance < 0) {
              replyTo ! CommandFailure("Initial balance cannot be negative")
            } else {
              replyTo ! CommandSuccess(s"Account $accountId created successfully")
            }
          case BankAccountActor.DepositMoneyCmd(amount, replyTo) =>
            if (amount <= 0) {
              replyTo ! CommandFailure("Deposit amount must be positive")
            } else if (accountId == "non-existent-account") {
              replyTo ! CommandFailure(s"Account $accountId is not active")
            } else {
              replyTo ! CommandSuccess(s"Deposited $amount to account $accountId")
            }
          case BankAccountActor.WithdrawMoneyCmd(amount, replyTo) =>
            if (amount <= 0) {
              replyTo ! CommandFailure("Withdrawal amount must be positive")
            } else if (accountId == "non-existent-account") {
              replyTo ! CommandFailure(s"Account $accountId is not active")
            } else if (amount > 1000) { // Mock insufficient funds
              replyTo ! CommandFailure(s"Insufficient funds. Current balance: 1000.0, requested: $amount")
            } else {
              replyTo ! CommandSuccess(s"Withdrew $amount from account $accountId")
            }
          case BankAccountActor.GetBalanceCmd(replyTo) =>
            if (accountId == "non-existent-account") {
              replyTo ! CommandFailure(s"Account $accountId is not active")
            } else {
              replyTo ! CommandResponse.BalanceResponse(accountId, 1000.0)
            }
          case BankAccountActor.GetAccountDetailsCmd(replyTo) =>
            if (accountId == "non-existent-account") {
              replyTo ! CommandFailure(s"Account $accountId is not active")
            } else {
              val state = BankAccountState(
                accountId = accountId,
                balance = 1000.0,
                owner = "John Doe",
                isActive = true,
                lastUpdated = java.time.Instant.now()
              )
              replyTo ! CommandResponse.AccountDetailsResponse(state)
            }
          case BankAccountActor.CloseAccountCmd(replyTo) =>
            if (accountId == "non-existent-account") {
              replyTo ! CommandFailure(s"Account $accountId is not active")
            } else if (accountId == "already-closed-account") {
              replyTo ! CommandFailure(s"Account $accountId is already closed")
            } else {
              replyTo ! CommandSuccess(s"Account $accountId closed successfully")
            }
        }
        
        override def path: akka.actor.typed.ActorRef[BankAccountActor.Command]#Path = ???
      }
  }

  val mockShardingHelper = new MockShardingHelper()
  val routes = new BankAccountRoutes(mockShardingHelper).routes

  "BankAccountRoutes" should {

    "create an account successfully" in {
      val request = CreateAccountRequest("new-account", 1000.0, "John Doe")
      Post("/api/accounts", request) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("created successfully")
      }
    }

    "reject account creation with negative balance" in {
      val request = CreateAccountRequest("negative-balance-account", -100.0, "John Doe")
      Post("/api/accounts", request) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "error"
        response.message shouldBe "Initial balance cannot be negative"
      }
    }

    "get account balance successfully" in {
      Get("/api/accounts/test-account/balance") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.data shouldBe defined
        response.data.get.asJsObject.fields("balance").convertTo[Double] shouldBe 1000.0
      }
    }

    "get account details successfully" in {
      Get("/api/accounts/test-account") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.data shouldBe defined
        val accountData = response.data.get.asJsObject.fields("account").asJsObject
        accountData.fields("accountId").convertTo[String] shouldBe "test-account"
        accountData.fields("balance").convertTo[Double] shouldBe 1000.0
      }
    }

    "deposit money successfully" in {
      val request = DepositRequest(500.0)
      Post("/api/accounts/test-account/deposit", request) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("Deposited 500.0")
      }
    }

    "reject deposit with negative amount" in {
      val request = DepositRequest(-100.0)
      Post("/api/accounts/test-account/deposit", request) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "error"
        response.message shouldBe "Deposit amount must be positive"
      }
    }

    "withdraw money successfully" in {
      val request = WithdrawRequest(300.0)
      Post("/api/accounts/test-account/withdraw", request) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("Withdrew 300.0")
      }
    }

    "reject withdrawal with insufficient funds" in {
      val request = WithdrawRequest(1500.0)
      Post("/api/accounts/test-account/withdraw", request) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "error"
        response.message should include("Insufficient funds")
      }
    }

    "close account successfully" in {
      Post("/api/accounts/test-account/close") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("closed successfully")
      }
    }

    "handle non-existent account operations" in {
      Get("/api/accounts/non-existent-account/balance") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "error"
        response.message should include("is not active")
      }
    }

    "return health check" in {
      Get("/api/accounts/health") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message shouldBe "Bank Account API is running"
      }
    }
  }
}

package com.clusteredbankservice.http

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.clusteredbankservice.actor.BankAccountActor
import com.clusteredbankservice.config.TestConfig
import com.clusteredbankservice.mock.MockBankAccountService
import com.clusteredbankservice.sharding.BankAccountSharding.{BankAccountEntityKey, ShardingHelper}
import com.typesafe.config.Config
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class BankAccountRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with JsonFormats with TestConfig {

  lazy val testKit = ActorTestKit("BankAccountRoutesSpec", unitTestConfig)
  override def testConfig: Config = unitTestConfig

  implicit val timeout: Timeout = 30.seconds
  implicit val typedSystem: ActorSystem[_] = testKit.system

  class MockShardingHelper(mockService: MockBankAccountService) extends ShardingHelper(null) {
    override def getAccountEntity(accountId: String): EntityRef[BankAccountActor.Command] = {
      TestEntityRef(BankAccountEntityKey, accountId, testKit.spawn(mockService.createMockBehavior(accountId)))
    }
  }

  val mockShardingHelper = new MockShardingHelper(new MockBankAccountService(testKit))
  val routes: Route = new BankAccountRoutes(mockShardingHelper).routes


  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

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
        val accountData = response.data.get.asJsObject
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
        response.message shouldBe "Clustered Bank Service is running"
      }
    }
  }
}

package com.clusteredbankservice

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, JoinSeedNodes}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.util.Timeout
import com.clusteredbankservice.actor.BankAccountActor
import com.clusteredbankservice.config.TestConfig
import com.clusteredbankservice.http._
import com.clusteredbankservice.sharding.BankAccountSharding.{BankAccountEntityKey, ShardingHelper}
import com.typesafe.config.Config
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class IntegrationTest extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalatestRouteTest with JsonFormats with TestConfig {

  override def testConfig: Config = integrationTestConfig
  val testKit = ActorTestKit("IntegrationTest", integrationTestConfig)


  override protected def beforeAll(): Unit = {
    // Setup cluster
    val cluster = Cluster(testKit.system)
    cluster.manager ! JoinSeedNodes(List(cluster.selfMember.address))

    // Initialize sharding
    val sharding = ClusterSharding(testKit.system)
    sharding.init(Entity(BankAccountEntityKey)(entityContext =>
      BankAccountActor(entityContext.entityId)))

    // Wait for cluster to form
    Thread.sleep(1000)

    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  implicit val timeout: Timeout = 30.seconds
  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(30.seconds)
//  implicit val ec: ExecutionContext = testKit.system.executionContext
  implicit val typedSystem: ActorSystem[_] = testKit.system

  "The complete Akka API system" should {

    "work end-to-end with real actors" in {
      // Setup cluster
      val cluster = Cluster(testKit.system)
      cluster.manager ! JoinSeedNodes(List(cluster.selfMember.address))

      // Setup real sharding
      val sharding = ClusterSharding(testKit.system)
      val shardingHelper = ShardingHelper(sharding)
      
      // Setup HTTP routes
      val routes = BankAccountRoutes(shardingHelper).routes

      // Test creating an account
      val createRequest = CreateAccountRequest("integration-test-account", 1000.0, "Integration Test User")
      Post("/api/accounts", createRequest) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("created successfully")
      }

      // Allow some time for the actor to be created
      Thread.sleep(100)

      // Test getting balance
      Get("/api/accounts/integration-test-account/balance") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.data shouldBe defined
        val balance = response.data.get.asJsObject.fields("balance").convertTo[Double]
        balance shouldBe 1000.0
      }

      // Test deposit
      val depositRequest = DepositRequest(500.0)
      Post("/api/accounts/integration-test-account/deposit", depositRequest) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("Deposited 500.0")
      }

      // Allow some time for the event to be processed
      Thread.sleep(100)

      // Test updated balance
      Get("/api/accounts/integration-test-account/balance") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        val balance = response.data.get.asJsObject.fields("balance").convertTo[Double]
        balance shouldBe 1500.0
      }

      // Test withdrawal
      val withdrawRequest = WithdrawRequest(300.0)
      Post("/api/accounts/integration-test-account/withdraw", withdrawRequest) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("Withdrew 300.0")
      }

      // Allow some time for the event to be processed
      Thread.sleep(100)

      // Test final balance
      Get("/api/accounts/integration-test-account/balance") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        val balance = response.data.get.asJsObject.fields("balance").convertTo[Double]
        balance shouldBe 1200.0
      }

      // Test account details
      Get("/api/accounts/integration-test-account") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.data shouldBe defined
        val accountData = response.data.get.asJsObject
        accountData.fields("accountId").convertTo[String] shouldBe "integration-test-account"
        accountData.fields("balance").convertTo[Double] shouldBe 1200.0
        accountData.fields("owner").convertTo[String] shouldBe "Integration Test User"
        accountData.fields("isActive").convertTo[Boolean] shouldBe true
      }

      // Test closing account
      Post("/api/accounts/integration-test-account/close") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        response.message should include("closed successfully")
      }

      // Allow some time for the event to be processed
      Thread.sleep(100)

      // Test operations on closed account
      val depositAfterCloseRequest = DepositRequest(100.0)
      Post("/api/accounts/integration-test-account/deposit", depositAfterCloseRequest) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "error"
        response.message should include("not active")
      }
    }

    "handle concurrent operations correctly" in {
      // Setup cluster and sharding
      val cluster = Cluster(testKit.system)
      cluster.manager ! JoinSeedNodes(List(cluster.selfMember.address))
      
      val sharding = ClusterSharding(testKit.system)
      val shardingHelper = ShardingHelper(sharding)
      val routes = BankAccountRoutes(shardingHelper).routes

      // Create account
      val createRequest = CreateAccountRequest("concurrent-test-account", 1000.0, "Concurrent Test User")
      Post("/api/accounts", createRequest) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
      }

      Thread.sleep(100)

      // Perform multiple concurrent deposits
      val depositFutures = (1 to 5).map { _ =>
        val depositRequest = DepositRequest(100.0)
        Future {
          Post("/api/accounts/concurrent-test-account/deposit", depositRequest) ~> routes ~> check {
            status shouldBe StatusCodes.OK
            val response = responseAs[ApiResponse]
            response.status shouldBe "success"
          }
        }
      }

      // Wait for all deposits to complete
      import scala.concurrent.Await
      Await.result(Future.sequence(depositFutures), 10.seconds)

      Thread.sleep(200)

      // Check final balance
      Get("/api/accounts/concurrent-test-account/balance") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ApiResponse]
        response.status shouldBe "success"
        val balance = response.data.get.asJsObject.fields("balance").convertTo[Double]
        balance shouldBe 1500.0 // 1000 + 5 * 100
      }
    }
  }
}

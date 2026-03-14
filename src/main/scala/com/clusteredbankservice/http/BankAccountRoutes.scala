package com.clusteredbankservice.http

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.clusteredbankservice.actor.BankAccountActor
import com.clusteredbankservice.domain._
import com.clusteredbankservice.sharding.BankAccountSharding.ShardingHelper
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class BankAccountRoutes(shardingHelper: ShardingHelper)(implicit system: ActorSystem[_], ec: ExecutionContext) extends JsonFormats {
  
  implicit private val timeout: Timeout = 30.seconds
  implicit private val scheduler: Scheduler = system.scheduler

  // Helper to convert CommandResponse to ApiResponse
  private def commandResponseToApiResponse(response: CommandResponse): ApiResponse = response match {
    case CommandSuccess(message: String) => ApiResponse("success", message, None)
    case CommandFailure(reason: String) => ApiResponse("error", reason, None)
    case BalanceResponse(accountId, balance) => 
      ApiResponse("success", "Balance retrieved", Some(JsObject("accountId" -> JsString(accountId), "balance" -> JsNumber(balance))))
    case AccountDetailsResponse(state) => 
      ApiResponse("success", "Account details retrieved", Some(state.toJson))
  }

  val routes: Route = pathPrefix("api" / "accounts") {
    concat(
      // Health check
      path("health") {
        get {
          complete(ApiResponse("success", "Clustered Bank Service is running", None))
        }
      },

      // Create account
      post {
        entity(as[CreateAccountRequest]) { request =>
          val responseFuture: Future[CommandResponse] = 
            shardingHelper.getAccountEntity(request.accountId)
              .ask[CommandResponse](replyTo => BankAccountActor.CreateAccountCmd(request.accountId, request.initialBalance, request.owner, replyTo))
          
          complete(responseFuture.map(commandResponseToApiResponse))
        }
      },
      
      // Get account balance
      path(Segment / "balance") { accountId =>
        get {
          val responseFuture: Future[CommandResponse] = 
            shardingHelper.getAccountEntity(accountId)
              .ask[CommandResponse](replyTo => BankAccountActor.GetBalanceCmd(accountId, replyTo))
          
          complete(responseFuture.map(commandResponseToApiResponse))
        }
      },
      
      // Get account details
      path(Segment) { accountId =>
        get {
          val responseFuture: Future[CommandResponse] = 
            shardingHelper.getAccountEntity(accountId)
              .ask[CommandResponse](replyTo => BankAccountActor.GetAccountDetailsCmd(accountId, replyTo))
          
          complete(responseFuture.map(commandResponseToApiResponse))
        }
      },
      
      // Deposit money
      path(Segment / "deposit") { accountId =>
        post {
          entity(as[DepositRequest]) { request =>
            val responseFuture: Future[CommandResponse] = 
              shardingHelper.getAccountEntity(accountId)
                .ask[CommandResponse](replyTo => BankAccountActor.DepositMoneyCmd(accountId, request.amount, replyTo))
            
            complete(responseFuture.map(commandResponseToApiResponse))
          }
        }
      },
      
      // Withdraw money
      path(Segment / "withdraw") { accountId =>
        post {
          entity(as[WithdrawRequest]) { request =>
            val responseFuture: Future[CommandResponse] = 
              shardingHelper.getAccountEntity(accountId)
                .ask[CommandResponse](replyTo => BankAccountActor.WithdrawMoneyCmd(accountId, request.amount, replyTo))
            
            complete(responseFuture.map(commandResponseToApiResponse))
          }
        }
      },
      
      // Close account
      path(Segment / "close") { accountId =>
        post {
          val responseFuture: Future[CommandResponse] = 
            shardingHelper.getAccountEntity(accountId)
              .ask[CommandResponse](replyTo => BankAccountActor.CloseAccountCmd(accountId, replyTo))
          
          complete(responseFuture.map(commandResponseToApiResponse))
        }
      }

    )
  }
}

object BankAccountRoutes {
  def apply(shardingHelper: ShardingHelper)(implicit system: ActorSystem[_], ec: ExecutionContext): BankAccountRoutes =
    new BankAccountRoutes(shardingHelper)
}

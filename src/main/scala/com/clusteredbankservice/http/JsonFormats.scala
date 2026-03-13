package com.clusteredbankservice.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.clusteredbankservice.domain.{AccountDetailsResponse, BalanceResponse, BankAccountState, CommandFailure, CommandResponse, CommandSuccess}
import spray.json._
import com.clusteredbankservice.domain._

import java.time.Instant
import java.time.format.DateTimeFormatter

trait JsonFormats extends SprayJsonSupport with DefaultJsonProtocol {
  
  // Custom Instant format
  implicit object InstantFormat extends RootJsonFormat[Instant] {
    private val formatter = DateTimeFormatter.ISO_INSTANT
    
    def write(instant: Instant): JsValue = JsString(formatter.format(instant))
    def read(value: JsValue): Instant = value match {
      case JsString(str) => Instant.parse(str)
      case _ => deserializationError("Expected ISO date-time string")
    }
  }

  // Domain models
  implicit val instantFormat: RootJsonFormat[Instant] = InstantFormat
  implicit val bankAccountStateFormat: RootJsonFormat[BankAccountState] = jsonFormat5(BankAccountState.apply)
  implicit val commandResponseFormat: RootJsonFormat[CommandResponse] = new RootJsonFormat[CommandResponse] {
    def write(response: CommandResponse): JsValue = response match {
      case CommandSuccess(message) => 
        JsObject("type" -> JsString("success"), "message" -> JsString(message))
      case CommandFailure(reason) => 
        JsObject("type" -> JsString("failure"), "reason" -> JsString(reason))
      case BalanceResponse(accountId, balance) => 
        JsObject("type" -> JsString("balance"), "accountId" -> JsString(accountId), "balance" -> JsNumber(balance))
      case AccountDetailsResponse(state) => 
        JsObject("type" -> JsString("accountDetails"), "account" -> bankAccountStateFormat.write(state))
    }
    
    def read(value: JsValue): CommandResponse = {
      val obj = value.asJsObject
      obj.fields.get("type") match {
        case Some(JsString("success")) => 
          CommandSuccess(obj.fields("message").convertTo[String])
        case Some(JsString("failure")) => 
          CommandFailure(obj.fields("reason").convertTo[String])
        case Some(JsString("balance")) => 
          BalanceResponse(
            obj.fields("accountId").convertTo[String],
            obj.fields("balance").convertTo[Double]
          )
        case Some(JsString("accountDetails")) => 
          AccountDetailsResponse(obj.fields("account").convertTo[BankAccountState])
        case _ => deserializationError("Unknown response type")
      }
    }
  }

  // HTTP request/response models
  implicit val createAccountRequestFormat: RootJsonFormat[CreateAccountRequest] = jsonFormat3(CreateAccountRequest)
  implicit val depositRequestFormat: RootJsonFormat[DepositRequest] = jsonFormat1(DepositRequest)
  implicit val withdrawRequestFormat: RootJsonFormat[WithdrawRequest] = jsonFormat1(WithdrawRequest)
  implicit val apiResponseFormat: RootJsonFormat[ApiResponse] = jsonFormat3(ApiResponse)
}

// HTTP DTOs
case class CreateAccountRequest(accountId: String, initialBalance: Double, owner: String)
case class DepositRequest(amount: Double)
case class WithdrawRequest(amount: Double)
case class ApiResponse(status: String, message: String, data: Option[JsValue] = None)

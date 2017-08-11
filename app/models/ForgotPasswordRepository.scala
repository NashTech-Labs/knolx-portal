package models

import javax.inject.Inject

import models.passwordChangeRequestJsonFormats._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import reactivemongo.play.json.collection.JSONCollection
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class PasswordChangeRequestInfo(email: String,
                                     token: String,
                                     activeTill: BSONDateTime,
                                     active: Boolean = true)

object passwordChangeRequestJsonFormats {

  import play.api.libs.json.Json

  implicit val passwordChangeRequestInfoFormat = Json.format[PasswordChangeRequestInfo]

}

class ForgotPasswordRepository @Inject()(reactiveMongoApi: ReactiveMongoApi, dateTimeUtility: DateTimeUtility) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("forgotpassword"))

  def upsert(passwordChangeRequest: PasswordChangeRequestInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val selector = BSONDocument("email" -> passwordChangeRequest.email)

    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "email" -> passwordChangeRequest.email,
          "token" -> passwordChangeRequest.token,
          "activeTill" -> passwordChangeRequest.activeTill,
          "active" -> passwordChangeRequest.active
        ))

    collection.flatMap(_.update(selector, modifier, upsert = true))

  }

  def getPasswordChangeRequest(token: String, email: Option[String])(implicit ex: ExecutionContext): Future[Option[PasswordChangeRequestInfo]] = {
    val millis = dateTimeUtility.nowMillis

    val condition = email match {
      case Some(emailId) => Json.obj("token" -> token,
        "email" -> emailId,
        "activeTill" -> BSONDocument("$gt" -> BSONDateTime(millis)),
        "active" -> true)

      case None => Json.obj("token" -> token, "activeTill" -> BSONDocument("$gt" -> BSONDateTime(millis)), "active" -> true)
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .cursor[PasswordChangeRequestInfo](ReadPreference.Primary)
          .headOption)
  }

}

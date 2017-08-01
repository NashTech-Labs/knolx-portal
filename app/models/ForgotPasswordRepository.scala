package models

import javax.inject.Inject

import play.api.libs.json.OFormat
import models.ForgotPasswordJsonFormats._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class PasswordChangeRequestInfo(email: String,
                                     token: String,
                                     activeTill: BSONDateTime)

object ForgotPasswordJsonFormats {

  import play.api.libs.json.Json

  implicit val passwordChangeRequestInfoFormat: OFormat[PasswordChangeRequestInfo] = Json.format[PasswordChangeRequestInfo]

}

class ForgotPasswordRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("forgotpassword"))

  def upsert(passwordChangeRequest: PasswordChangeRequestInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val selector = BSONDocument("email" -> passwordChangeRequest.email)

    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "email" -> passwordChangeRequest.email,
          "token" -> passwordChangeRequest.token,
          "activeTill" -> passwordChangeRequest.activeTill
        ))

    collection.flatMap(_.update(selector, modifier, upsert = true))

  }

}

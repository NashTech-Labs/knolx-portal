package models

import javax.inject.Inject

import models.UserJsonFormats._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import utilities.PasswordUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class UserInfo(email: String,
                    password: String,
                    algorithm: String,
                    active: Boolean,
                    admin: Boolean,
                    _id: BSONObjectID = BSONObjectID.generate)

case class UpdatedUserInfo(id: String, active: Boolean, password: Option[String])

object UserJsonFormats {

  import play.api.libs.json.Json

  implicit val userFormat = Json.format[UserInfo]
}

class UsersRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  def getByEmail(email: String)(implicit ex: ExecutionContext): Future[Option[UserInfo]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("email" -> email.toLowerCase))
          .cursor[UserInfo](ReadPreference.Primary).headOption)
  }

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("users"))

  def insert(user: UserInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(user))

  def update(updatedRecord: UpdatedUserInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> updatedRecord.id))
    val modifier = updatedRecord.password match {
      case Some(password) => BSONDocument(
        "$set" -> BSONDocument(
          "active" -> updatedRecord.active,
          "password" -> PasswordUtility.encrypt(password))
      )
      case None => BSONDocument(
        "$set" -> BSONDocument(
          "active" -> updatedRecord.active
        ))
    }
    collection.flatMap(jsonCollection =>
      jsonCollection.update(selector, modifier))
  }

}

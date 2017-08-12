package models

import javax.inject.Inject

import models.UserJsonFormats._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{QueryOpts, ReadPreference}
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

case class UpdatedUserInfo(email: String, active: Boolean, password: Option[String])

object UserJsonFormats {

  import play.api.libs.json.Json

  val pageSize = 10

  implicit val userFormat = Json.format[UserInfo]
}

class UsersRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("users"))

  def getByEmail(email: String)(implicit ex: ExecutionContext): Future[Option[UserInfo]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("email" -> email.toLowerCase))
          .cursor[UserInfo](ReadPreference.Primary).headOption)
  }

  def getActiveByEmail(email: String)(implicit ex: ExecutionContext): Future[Option[UserInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("email" -> email.toLowerCase, "active" -> true))
          .cursor[UserInfo](ReadPreference.Primary).headOption)

  def insert(user: UserInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(user))

  def update(updatedRecord: UpdatedUserInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("email" -> updatedRecord.email)
    val modifier = updatedRecord.password match {
      case Some(password) =>
        BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active, "password" -> PasswordUtility.encrypt(password)))
      case None           =>
        BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active))
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def delete(email: String)(implicit ex: ExecutionContext): Future[Option[UserInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .findAndUpdate(
            BSONDocument("email" -> email, "admin" -> false),
            BSONDocument("$set" -> BSONDocument("active" -> false)),
            fetchNewObject = true,
            upsert = false)
          .map(_.result[UserInfo]))

  def paginate(pageNumber: Int, keyword: Option[String] = None)(implicit ex: ExecutionContext): Future[List[UserInfo]] = {
    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)

    val condition = keyword match {
      case Some(key) => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")))
      case None      => Json.obj()
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .options(queryOptions)
          .cursor[UserInfo](ReadPreference.Primary)
          .collect[List](pageSize, FailOnError[List[UserInfo]]()))
  }

  def userCountWithKeyword(keyword: Option[String] = None)(implicit ex: ExecutionContext): Future[Int] = {
    val condition = keyword match {
      case Some(key) => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*"))))
      case None      => None
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection.count(condition))
  }

}

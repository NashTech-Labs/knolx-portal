package models

import java.time.LocalDateTime
import javax.inject.Inject

import models.UserJsonFormats._
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats.BSONDocumentFormat
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import utilities.{DateTimeUtility, PasswordUtility}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class UserInfo(email: String,
                    password: String,
                    algorithm: String,
                    active: Boolean,
                    admin: Boolean,
                    coreMember :Boolean,
                    superUser :Boolean,
                    banTill: BSONDateTime,
                    banCount: Int = 0,
                    _id: BSONObjectID = BSONObjectID.generate)

case class UpdatedUserInfo(email: String,
                           active: Boolean,
                           ban: Boolean,
                           coreMember :Boolean,
                           password: Option[String])

object UserJsonFormats {

  import play.api.libs.json.Json

  val pageSize = 10
  val banPeriod = 30

  implicit val userFormat = Json.format[UserInfo]
}

class UsersRepository @Inject()(reactiveMongoApi: ReactiveMongoApi, dateTimeUtility: DateTimeUtility) {

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

  def getActiveAndBanned(email: String)(implicit ex: ExecutionContext): Future[Option[UserInfo]] = {
    val millis = dateTimeUtility.nowMillis
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("email" -> email.toLowerCase,
            "active" -> true,
            "banTill" -> BSONDocument("$gte" -> BSONDateTime(millis))))
          .cursor[UserInfo](ReadPreference.Primary).headOption)
  }

  def getAllActiveEmails(implicit ex: ExecutionContext): Future[List[String]] = {
    val millis = dateTimeUtility.nowMillis
    val query = Json.obj("active" -> true, "banTill" -> BSONDocument("$lt" -> BSONDateTime(millis)))
    val projection = Json.obj("email" -> 1)

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(query, projection)
          .cursor[JsValue](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[JsValue]]())
      ).map(_.flatMap(_ ("email").asOpt[String]))
  }

  def insert(user: UserInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(user))

  def update(updatedRecord: UpdatedUserInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val banTill: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.nowMillis).plusDays(banPeriod)
    val duration = BSONDateTime(dateTimeUtility.toMillis(banTill))
    val unban = BSONDateTime(dateTimeUtility.nowMillis)

    val selector = BSONDocument("email" -> updatedRecord.email)
    val modifier = (updatedRecord.password, updatedRecord.ban) match {
      case (Some(password), true)  =>
        BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active, "password" -> PasswordUtility.encrypt(password), "banTill" -> duration, "coreMember" -> updatedRecord.coreMember))
      case (Some(password), false) =>
        BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active, "password" -> PasswordUtility.encrypt(password), "banTill" -> unban, "coreMember" -> updatedRecord.coreMember))
      case (None, true)            =>
        BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active, "banTill" -> duration, "coreMember" -> updatedRecord.coreMember))
      case (None, false)           =>
        BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active, "banTill" -> unban, "coreMember" -> updatedRecord.coreMember))
      }

    collection
      .flatMap(jsonCollection  =>
        jsonCollection.update(selector, modifier))
  }

  def ban(email: String)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val banTill: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.nowMillis).plusDays(banPeriod)

    val duration = BSONDateTime(dateTimeUtility.toMillis(banTill))

    val selector = BSONDocument("email" -> email)
    val modifier = BSONDocument("$set" -> BSONDocument("banTill" -> duration), "$inc" -> BSONDocument("banCount" -> 1))

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

  def paginate(pageNumber: Int, keyword: Option[String] = None, filter: String = "all")(implicit ex: ExecutionContext): Future[List[UserInfo]] = {
    val millis = dateTimeUtility.nowMillis
    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)

    val condition = (keyword, filter) match {
      case (Some(key), "all")       => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")))
      case (Some(key), "banned")    => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$gte" -> BSONDateTime(millis)))
      case (Some(key), "allowed")   => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$lt" -> BSONDateTime(millis)))
      case (Some(key), "active")    => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "active" -> true)
      case (Some(key), "suspended") => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "active" -> false)
      case (None, "all")            => Json.obj()
      case (None, "banned")         => Json.obj("banTill" -> BSONDocument("$gte" -> BSONDateTime(millis)))
      case (None, "allowed")        => Json.obj("banTill" -> BSONDocument("$lt" -> BSONDateTime(millis)))
      case (None, "active")         => Json.obj("active" -> true)
      case (None, "suspended")      => Json.obj("active" -> false)
      case _                        => Json.obj()
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .options(queryOptions)
          .cursor[UserInfo](ReadPreference.Primary)
          .collect[List](pageSize, FailOnError[List[UserInfo]]()))
  }

  def userCountWithKeyword(keyword: Option[String] = None, filter: String = "all")(implicit ex: ExecutionContext): Future[Int] = {

    val millis = dateTimeUtility.nowMillis
    val condition = (keyword, filter) match {
      case (Some(key), "all")       => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*"))))
      case (Some(key), "banned")    => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$gte" -> BSONDateTime(millis))))
      case (Some(key), "allowed")   => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$lt" -> BSONDateTime(millis))))
      case (Some(key), "active")    => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "active" -> true))
      case (Some(key), "suspended") => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "active" -> false))
      case (None, "all")            => None
      case (None, "banned")         => Some(Json.obj("banTill" -> BSONDocument("$gte" -> BSONDateTime(millis))))
      case (None, "allowed")        => Some(Json.obj("banTill" -> BSONDocument("$lt" -> BSONDateTime(millis))))
      case (None, "active")         => Some(Json.obj("active" -> true))
      case (None, "suspended")      => Some(Json.obj("active" -> false))
      case _                        => None
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection.count(condition))
  }

}

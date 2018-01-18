package models

import java.time.LocalDateTime
import javax.inject.Inject

import models.UserJsonFormats._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
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
                    _id: BSONObjectID = BSONObjectID.generate,
                    approved: Boolean = false)

case class UpdatedUserInfo(email: String,
                           active: Boolean,
                           ban: Boolean,
                           coreMember: Boolean,
                           admin: Boolean,
                           password: Option[String])

object UserJsonFormats {

  import play.api.libs.json.Json

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

  def update(updatedRecord: UpdatedUserInfo)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {

    val banTill: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.nowMillis).plusDays(banPeriod)
    val duration = BSONDateTime(dateTimeUtility.toMillis(banTill))
    val unban = BSONDateTime(dateTimeUtility.nowMillis)

    collection
      .flatMap(jsonCollection => jsonCollection.find(BSONDocument("email" -> updatedRecord.email))
        .cursor[UserInfo](ReadPreference.Primary)
        .collect[List](-1, FailOnError[List[UserInfo]]())
        .flatMap(listOfUserInfo =>

          listOfUserInfo.headOption.fold(
            Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
          ) { user =>
            val banPeriod = if (user.banTill.value > unban.value) user.banTill else duration
            val selector = BSONDocument("email" -> updatedRecord.email)
            val modifier = (updatedRecord.password, updatedRecord.ban) match {
              case (Some(password), true)  =>
                BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active, "password" -> PasswordUtility.encrypt(password),
                  "banTill" -> banPeriod, "coreMember" -> updatedRecord.coreMember, "admin" -> updatedRecord.admin))
              case (Some(password), false) =>
                BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active, "password" -> PasswordUtility.encrypt(password),
                  "banTill" -> unban, "coreMember" -> updatedRecord.coreMember, "admin" -> updatedRecord.admin))
              case (None, true)            =>
                BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active,
                  "banTill" -> banPeriod, "coreMember" -> updatedRecord.coreMember, "admin" -> updatedRecord.admin))
              case (None, false)           =>
                BSONDocument("$set" -> BSONDocument("active" -> updatedRecord.active,
                  "banTill" -> unban, "coreMember" -> updatedRecord.coreMember, "admin" -> updatedRecord.admin))
            }
            jsonCollection.update(selector, modifier)
          }
        )
      )
  }

  def updatePassword(email: String, password: String)(implicit ex: ExecutionContext): Future[WriteResult] = {

     val modifier = BSONDocument("$set" -> BSONDocument("password" -> PasswordUtility.encrypt(password)))
        collection
          .flatMap(jsonCollection =>
            jsonCollection.update(BSONDocument("email" -> email), modifier))
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

  def paginate(pageNumber: Int,
               keyword: Option[String] = None,
               filter: String = "all",
               pageSize: Int = 10)(implicit ex: ExecutionContext): Future[List[UserInfo]] = {
    val millis = dateTimeUtility.nowMillis
    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)

    val condition = (keyword, filter) match {
      case (Some(key), "all")       => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")))
      case (Some(key), "banned")    => Json.obj("email" ->
        Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$gte" -> BSONDateTime(millis)))
      case (Some(key), "allowed")   => Json.obj("email" ->
        Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$lt" -> BSONDateTime(millis)))
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
      case (Some(key), "banned")    => Some(Json.obj("email" ->
        Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$gte" -> BSONDateTime(millis))))
      case (Some(key), "allowed")   => Some(Json.obj("email" ->
        Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "banTill" -> BSONDocument("$lt" -> BSONDateTime(millis))))
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

  def userListSearch(keyword: Option[String] = None)(implicit ex: ExecutionContext): Future[List[String]] = {

    val condition = keyword match {
      case Some(key) => Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "active" -> true)
      case None => Json.obj("active" -> true)
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .cursor[JsValue](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[JsValue]]())
      ).map(_.flatMap(_ ("email").asOpt[String]))
  }

  def getAllAdminAndSuperUser(implicit ex: ExecutionContext): Future[List[String]] = {
    val condition = Json.obj("$or" -> List(Json.obj("admin" -> true), Json.obj("superUser" -> true)))

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .cursor[JsValue](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[JsValue]]())
      ).map(_.flatMap(_ ("email").asOpt[String]))
  }

  def approveUser(id: String): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))

    val modifier = BSONDocument("$set" -> BSONDocument("approved" -> true))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def getUserById(id: String)(implicit ex: ExecutionContext): Future[Option[UserInfo]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("_id" -> BSONDocument("$oid" -> id)))
          .cursor[UserInfo](ReadPreference.Primary).headOption)
  }

}

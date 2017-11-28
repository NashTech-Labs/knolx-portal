package models

import javax.inject.Inject

import actors.SessionsScheduler.{EmailOnce, Notification, Reminder}
import controllers.{FilterUserSessionInformation, UpdateSessionInformation}
import models.SessionJsonFormats._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class SessionInfo(userId: String,
                       email: String,
                       date: BSONDateTime,
                       session: String,
                       category: String,
                       subCategory: String,
                       feedbackFormId: String,
                       topic: String,
                       feedbackExpirationDays: Int,
                       meetup: Boolean,
                       rating: String,
                       score: Double,
                       cancelled: Boolean,
                       active: Boolean,
                       expirationDate: BSONDateTime,
                       youtubeURL: Option[String],
                       slideShareURL: Option[String],
                       reminder: Boolean = false,
                       notification: Boolean = false,
                       _id: BSONObjectID = BSONObjectID.generate
                      )

case class UpdateSessionInfo(sessionUpdateFormData: UpdateSessionInformation,
                             expirationDate: BSONDateTime)

object SessionJsonFormats {

  import play.api.libs.json.Json

  implicit val sessionFormat = Json.format[SessionInfo]

  sealed trait SessionState
  case object ExpiringNext extends SessionState
  case object ExpiringNextNotReminded extends SessionState
  case object SchedulingNext extends SessionState
  case object SchedulingNextUnNotified extends SessionState

}

class SessionsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi, dateTimeUtility: DateTimeUtility) {

  import play.modules.reactivemongo.json._

  val pageSize = 10

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("sessions"))

  def delete(id: String)(implicit ex: ExecutionContext): Future[Option[JsObject]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .findAndUpdate(
            BSONDocument("_id" -> BSONDocument("$oid" -> id)),
            BSONDocument("$set" -> BSONDocument("active" -> false)),
            fetchNewObject = true,
            upsert = false)
          .map(_.value))

  def sessionsForToday(sessionState: SessionState)(implicit ex: ExecutionContext): Future[List[SessionInfo]] = {
    val millis = dateTimeUtility.nowMillis
    val endOfTheDay = dateTimeUtility.endOfDayMillis

    val condition = sessionState match {
      case SchedulingNext           =>
        Json.obj("cancelled" -> false, "active" -> true, "date" -> BSONDocument("$gte" -> BSONDateTime(millis),
          "$lte" -> BSONDateTime(endOfTheDay)))
      case ExpiringNext             =>
        Json.obj("cancelled" -> false, "active" -> true, "expirationDate" -> BSONDocument("$gte" -> BSONDateTime(millis),
          "$lte" -> BSONDateTime(endOfTheDay)))
      case ExpiringNextNotReminded  =>
        Json.obj("cancelled" -> false, "active" -> true, "expirationDate" -> BSONDocument("$gte" -> BSONDateTime(millis),
          "$lte" -> BSONDateTime(endOfTheDay)), "reminder" -> false)
      case SchedulingNextUnNotified =>
        Json.obj("cancelled" -> false, "active" -> true, "date" -> BSONDocument("$gte" -> BSONDateTime(millis),
          "$lte" -> BSONDateTime(endOfTheDay)), "notification" -> false)
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .sort(Json.obj("date" -> 1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionInfo]]()))
  }

  def sessions(implicit ex: ExecutionContext): Future[List[SessionInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("active" -> true))
          .sort(Json.obj("date" -> 1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionInfo]]()))

  def getById(id: String)(implicit ex: ExecutionContext): Future[Option[SessionInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(BSONDocument("_id" -> BSONDocument("$oid" -> id)))
          .cursor[SessionInfo](ReadPreference.Primary)
          .headOption)

  def getActiveById(id: String)(implicit ex: ExecutionContext): Future[Option[SessionInfo]] = {
    val millis = dateTimeUtility.nowMillis

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(BSONDocument(
            "_id" -> BSONDocument("$oid" -> id),
            "active" -> true,
            "cancelled" -> false,
            "expirationDate" -> BSONDocument("$gt" -> BSONDateTime(millis)),
            "date" -> BSONDocument("$lt" -> BSONDateTime(millis))
          ))
          .cursor[SessionInfo](ReadPreference.Primary)
          .headOption)
  }

  def insert(session: SessionInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(session))

  def paginate(pageNumber: Int, keyword: Option[String] = None)(implicit ex: ExecutionContext): Future[List[SessionInfo]] = {
    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)
    val condition = keyword match {
      case Some(key) => Json.obj("$or" -> List(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*"))),
        Json.obj("topic" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "") + ".*"), "$options" -> "i"))), "active" -> true)
      case None      => Json.obj("active" -> true)
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .options(queryOptions)
          .sort(Json.obj("date" -> -1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List](pageSize, FailOnError[List[SessionInfo]]()))
  }

  def activeCount(keyword: Option[String] = None)(implicit ex: ExecutionContext): Future[Int] = {
    val condition = keyword match {
      case Some(key) => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")), "active" -> true))
      case None      => Some(Json.obj("active" -> true))
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection.count(condition))
  }

  def update(updatedRecord: UpdateSessionInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> updatedRecord.sessionUpdateFormData.id))
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "date" -> BSONDateTime(updatedRecord.sessionUpdateFormData.date.getTime),
        "topic" -> updatedRecord.sessionUpdateFormData.topic,
        "session" -> updatedRecord.sessionUpdateFormData.session,
        "category" -> updatedRecord.sessionUpdateFormData.category,
        "subCategory" -> updatedRecord.sessionUpdateFormData.subCategory,
        "feedbackFormId" -> updatedRecord.sessionUpdateFormData.feedbackFormId,
        "feedbackExpirationDays" -> updatedRecord.sessionUpdateFormData.feedbackExpirationDays,
        "meetup" -> updatedRecord.sessionUpdateFormData.meetup,
        "expirationDate" -> updatedRecord.expirationDate,
        "youtubeURL" -> updatedRecord.sessionUpdateFormData.youtubeURL,
        "slideShareURL" -> updatedRecord.sessionUpdateFormData.slideShareURL,
        "cancelled" -> updatedRecord.sessionUpdateFormData.cancelled)
    )

    collection.flatMap(jsonCollection =>
      jsonCollection.update(selector, modifier))
  }

  def activeSessions(email: Option[String] = None): Future[List[SessionInfo]] = {

    val millis = dateTimeUtility.nowMillis

    val condition = email.fold {
      Json.obj(
        "active" -> true,
        "cancelled" -> false,
        "expirationDate" -> BSONDocument("$gt" -> BSONDateTime(millis)),
        "date" -> BSONDocument("$lt" -> BSONDateTime(millis)))

    } { email =>
      Json.obj(
        "active" -> true,
        "cancelled" -> false,
        "email" -> email,
        "expirationDate" -> BSONDocument("$gt" -> BSONDateTime(millis)),
        "date" -> BSONDocument("$lt" -> BSONDateTime(millis)))
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .sort(Json.obj("date" -> -1))
          .cursor[SessionInfo](ReadPreference.primary)
          .collect[List](-1, FailOnError[List[SessionInfo]]()))

  }

  def userSessionsTillNow(email: Option[String] = None): Future[List[SessionInfo]] = {
    val millis = dateTimeUtility.nowMillis

    val condition = email.fold {
      Json.obj(
        "active" -> true,
        "cancelled" -> false,
        "date" -> BSONDocument("$lte" -> BSONDateTime(millis)))
    } { email =>
      Json.obj(
        "active" -> true,
        "cancelled" -> false,
        "email" -> email,
        "date" -> BSONDocument("$lte" -> BSONDateTime(millis)))
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .sort(Json.obj("date" -> -1))
          .cursor[SessionInfo](ReadPreference.primary)
          .collect[List](-1, FailOnError[List[SessionInfo]]()))

  }

  def immediatePreviousExpiredSessions: Future[List[SessionInfo]] = {
    val millis = dateTimeUtility.nowMillis

    collection
      .flatMap { jsonCollection =>
        import jsonCollection.BatchCommands.AggregationFramework._

        jsonCollection
          .aggregate(
            firstOperator = Match(
              Json.obj(
                "active" -> true,
                "cancelled" -> false,
                "expirationDate" -> BSONDocument("$lt" -> BSONDateTime(millis)))
            ),
            otherOperators = List(
              Group(Json.obj("$dateToString" -> Json.obj("format" -> "%Y-%m-%d", "date" -> "$date")))("sessions" -> PushField("$ROOT")),
              Sort(Descending("_id")),
              Limit(1)
            ))
          .map(_.firstBatch.flatMap(_ \\ "sessions").flatMap(_.validateOpt[List[SessionInfo]].getOrElse(None)))
          .map(_.flatten)
      }
  }

  def upsertRecord(session: SessionInfo, emailType: EmailOnce)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> session._id.stringify))

    val modifier = emailType match {
      case Reminder     => BSONDocument("$set" -> BSONDocument("reminder" -> true))
      case Notification => BSONDocument("$set" -> BSONDocument("notification" -> true))
    }

    collection.flatMap(_.update(selector, modifier, upsert = true))
  }

  def updateRating(sessionId: String, scores: List[Double]): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> sessionId))
    val scoresWithoutZero = scores.filterNot(_ == 0)
    val sessionScore = if(scoresWithoutZero.nonEmpty) scoresWithoutZero.sum / scoresWithoutZero.length else 0.00

    val updatedRating = sessionScore match {
      case good if sessionScore >= 60.00    => "Good"
      case average if sessionScore >= 30.00 => "Average"
      case _                                => "Bad"
    }

    val modifier = BSONDocument("$set" -> BSONDocument("score" -> sessionScore, "rating" -> updatedRating))

    collection
      .flatMap { jsonCollection =>
        jsonCollection
          .update(selector, modifier)
      }
  }

  def getVideoURL(sessionId: String): Future[List[String]] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> sessionId))
    val projection = Json.obj("youtubeURL" -> 1)

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector, projection)
          .cursor[JsValue](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[JsValue]]())
      ).map(_.flatMap(_ ("youtubeURL").asOpt[String]))
  }

  def updateVideoURL(sessionId: String, youtubeURL: String): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> sessionId))
    val modifier = BSONDocument(
      "$set" -> BSONDocument("youtubeURL" -> youtubeURL)
    )

    collection.flatMap(jsonCollection =>
      jsonCollection.update(selector, modifier))
  }

  def sessionsInTimeRange(filterUserSessionInformation: FilterUserSessionInformation): Future[List[SessionInfo]] = {

    val selector = filterUserSessionInformation.email match {
      case Some(email) => BSONDocument("email" -> email,
        "active" -> true, "cancelled" -> false,
        "date" -> BSONDocument("$gte" -> BSONDateTime(filterUserSessionInformation.startDate),
          "$lte" -> BSONDateTime(filterUserSessionInformation.endDate)))
      case None        => BSONDocument("active" -> true, "cancelled" -> false,
        "date" -> BSONDocument("$gte" -> BSONDateTime(filterUserSessionInformation.startDate),
          "$lte" -> BSONDateTime(filterUserSessionInformation.endDate)))
    }
    collection
      .flatMap(
        _.find(selector)
          .sort(Json.obj("date" -> 1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionInfo]]()))
  }


  def getMonthlyInfoSessions(filterUserSessionInformation: FilterUserSessionInformation): Future[List[(String, Int)]] = {
    collection
      .flatMap { jsonCollection =>
        import jsonCollection.BatchCommands.AggregationFramework._

        jsonCollection
          .aggregate(
            firstOperator = Match(
              Json.obj(
                "active" -> true,
                "cancelled" -> false,
                "date" -> BSONDocument("$gte" -> BSONDateTime(filterUserSessionInformation.startDate), "$lte" -> BSONDateTime(filterUserSessionInformation.endDate))
              )
            ),
            otherOperators = List(
              Group(Json.obj("$dateToString" -> Json.obj("format" -> "%Y-%m", "date" -> "$date")))("count" -> SumAll),
              Sort(Ascending("_id"))
            ))
          .map { aggRes =>
            aggRes
              .firstBatch
              .map(res => (res \ "_id", res \ "count"))
              .collect {
                case (dateLookup, knolxCountLookup) if dateLookup.isDefined && knolxCountLookup.isDefined =>
                  (dateLookup.get.validate[String].getOrElse(""), knolxCountLookup.get.validate[Int].getOrElse(0))
              }
              .filter { case (date, _) => date != "" }
          }
      }
  }
}

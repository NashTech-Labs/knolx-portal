package models

import javax.inject.Inject

import controllers.UpdateSessionInformation
import models.SessionJsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.api.commands.WriteResult
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
                       feedbackFormId: String,
                       topic: String,
                       meetup: Boolean,
                       rating: String,
                       cancelled: Boolean,
                       active: Boolean,
                       _id: BSONObjectID = BSONObjectID.generate)

object SessionJsonFormats {

  import play.api.libs.json.Json

  implicit val sessionFormat = Json.format[SessionInfo]

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

  def sessionsScheduledToday(implicit ex: ExecutionContext): Future[List[SessionInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj(
            "cancelled" -> false,
            "active" -> true,
            "date" -> BSONDocument(
              "$gte" -> BSONDateTime(dateTimeUtility.startOfDayMillis),
              "$lte" -> BSONDateTime(dateTimeUtility.endOfDayMillis))))
          .sort(Json.obj("date" -> 1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionInfo]]()))

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

  def insert(session: SessionInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(session))

  def paginate(pageNumber: Int)(implicit ex: ExecutionContext): Future[List[SessionInfo]] = {
    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("active" -> true))
          .options(queryOptions)
          .sort(Json.obj("date" -> 1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List](pageSize, FailOnError[List[SessionInfo]]()))
  }

  def activeCount(implicit ex: ExecutionContext): Future[Int] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection.count(Some(Json.obj("active" -> true))))

  def update(updatedRecord: UpdateSessionInformation)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> updatedRecord._id))

    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "date" -> BSONDateTime(updatedRecord.date.getTime),
        "topic" -> updatedRecord.topic,
        "session" -> updatedRecord.session,
        "feedbackFormId" -> updatedRecord.feedbackFormId,
        "meetup" -> updatedRecord.meetup)
    )

    collection.flatMap(jsonCollection =>
      jsonCollection.update(selector, modifier))
  }

  def getSessionsTillNow : Future[List[SessionInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj(
            "active" -> true,
            "cancelled" -> false,
            "date" -> BSONDocument("$lte" -> BSONDateTime(dateTimeUtility.nowMillis))))
          .sort(Json.obj("date" -> 1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List]())

  }

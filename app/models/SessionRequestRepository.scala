package models

import javax.inject.Inject

import controllers.UpdateApproveSessionInfo
import models.SessionRequestJsonFormats._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class SessionRequestInfo(email: String,
                              date: BSONDateTime,
                              category: String,
                              subCategory: String,
                              topic: String,
                              meetup: Boolean = false,
                              approved: Boolean = false,
                              decline: Boolean = false,
                              freeSlot: Boolean = false,
                              _id: BSONObjectID = BSONObjectID.generate
                             )

object SessionRequestJsonFormats {

  import play.api.libs.json.Json

  implicit val sessionRequestFormat = Json.format[SessionRequestInfo]
}

class SessionRequestRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("sessionrequest"))

  def insertSessionForApprove(approveSessionInfo: UpdateApproveSessionInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = approveSessionInfo.sessionId match {
      case id: String if id.nonEmpty => BSONDocument("_id" -> BSONDocument("$oid" -> approveSessionInfo.sessionId))
      case _                         => BSONDocument("_id" -> BSONObjectID.generate())
    }

    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "email" -> approveSessionInfo.email,
          "date" -> approveSessionInfo.date,
          "category" -> approveSessionInfo.category,
          "subCategory" -> approveSessionInfo.subCategory,
          "topic" -> approveSessionInfo.topic,
          "meetup" -> approveSessionInfo.meetup,
          "approved" -> approveSessionInfo.approved,
          "decline" -> approveSessionInfo.decline,
          "freeSlot" -> approveSessionInfo.freeSlot
        )
      )

    collection.flatMap(_.update(selector, modifier, upsert = true))
  }

  def getSession(sessionId: String): Future[SessionRequestInfo] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(BSONDocument("_id" -> BSONDocument("$oid" -> sessionId)))
          .cursor[SessionRequestInfo](ReadPreference.Primary).head)

  def getAllSessions(implicit ex: ExecutionContext): Future[List[SessionRequestInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj())
          .cursor[SessionRequestInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionRequestInfo]]()))

  def paginate(pageNumber: Int, keyword: Option[String] = None, pageSize: Int = 10)(implicit ex: ExecutionContext): Future[List[SessionRequestInfo]] = {
    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)
    val condition = keyword match {
      case Some(key) => Json.obj("$or" -> List(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*"))),
        Json.obj("topic" -> Json.obj("$regex" -> (".*" + key + ".*"), "$options" -> "i"))), "freeSlot" -> BSONDocument("$eq" -> false))
      case None      => Json.obj("freeSlot" -> BSONDocument("$eq" -> false))
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .options(queryOptions)
          .sort(Json.obj("decline" -> 1, "approved" -> 1))
          .cursor[SessionRequestInfo](ReadPreference.Primary)
          .collect[List](pageSize, FailOnError[List[SessionRequestInfo]]()))
  }

  def activeCount(keyword: Option[String] = None)(implicit ex: ExecutionContext): Future[Int] = {
    val condition = keyword match {
      case Some(key) => Some(Json.obj("email" -> Json.obj("$regex" -> (".*" + key.replaceAll("\\s", "").toLowerCase + ".*")),
        "freeSlot" -> BSONDocument("$eq" -> false)))
      case None      => Some(Json.obj("freeSlot" -> BSONDocument("$eq" -> false)))
    }

    collection
      .flatMap(jsonCollection =>
        jsonCollection.count(condition))
  }

  def getAllApprovedSession(implicit ex: ExecutionContext): Future[List[SessionRequestInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj("approved" -> true)).
          cursor[SessionRequestInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionRequestInfo]]()))

  def approveSession(sessionId: String)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> sessionId))
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "approved" -> true,
        "decline" -> false))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def declineSession(sessionId: String)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> sessionId))
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "approved" -> false,
        "decline" -> true))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def updateDateForPendingSession(sessionId: String, date: BSONDateTime): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> sessionId))
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "date" -> date))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def getAllPendingSession: Future[List[SessionRequestInfo]] = {
    val selector = BSONDocument("freeSlot" -> BSONDocument("$eq" -> false),
      "approved" -> BSONDocument("$eq" -> false),
      "decline" -> BSONDocument("$eq" -> false))

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .cursor[SessionRequestInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionRequestInfo]]()))
  }

  def deleteFreeSlot(id: String): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))

    collection
      .flatMap(_.remove(selector))
  }

  def getAllFreeSlots: Future[List[SessionRequestInfo]] = {
    val selector = BSONDocument("freeSlot" -> BSONDocument("$eq" -> true))

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .cursor[SessionRequestInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[SessionRequestInfo]]()))
  }

  def getFreeSlotByDate(date: BSONDateTime): Future[Option[SessionRequestInfo]] = {
    val selector = BSONDocument("date" -> date)

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .cursor[SessionRequestInfo](ReadPreference.Primary).headOption)
  }

}

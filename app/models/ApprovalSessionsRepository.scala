package models

import javax.inject.Inject

import controllers.UpdateApproveSessionInfo
import models.ApproveSessionJsonFormats._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class ApproveSessionInfo(email: String,
                              date: BSONDateTime,
                              category: String,
                              subCategory: String,
                              topic: String,
                              meetup: Boolean = false,
                              approved: Boolean = false,
                              decline: Boolean = false,
                              freeSlot: Boolean = false,
                              recommendationId: String = "",
                              _id: BSONObjectID = BSONObjectID.generate
                             )

object ApproveSessionJsonFormats {

  import play.api.libs.json.Json

  implicit val approveSessionFormat = Json.format[ApproveSessionInfo]
}

class ApprovalSessionsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("approvesessions"))

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
          "freeSlot" -> approveSessionInfo.freeSlot,
          "recommendationId" -> approveSessionInfo.recommendationId
        )
      )
    collection.flatMap(_.update(selector, modifier, upsert = true))
  }

  def getSession(sessionId: String): Future[ApproveSessionInfo] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(BSONDocument("_id" -> BSONDocument("$oid" -> sessionId)))
          .cursor[ApproveSessionInfo](ReadPreference.Primary).head)
  }

  def getAllSessions(implicit ex: ExecutionContext): Future[List[ApproveSessionInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj())
          .cursor[ApproveSessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[ApproveSessionInfo]]()))

  def getAllBookedSessions(implicit ex: ExecutionContext): Future[List[ApproveSessionInfo]] = {
    val selector = BSONDocument("freeSlot" -> BSONDocument("$eq" -> false))

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .sort(Json.obj("decline" -> 1, "approved" -> 1))
          .cursor[ApproveSessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[ApproveSessionInfo]]()))
  }

  def getAllApprovedSession(implicit ex: ExecutionContext): Future[List[ApproveSessionInfo]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj("approved" -> true)).
          cursor[ApproveSessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[ApproveSessionInfo]]()))
  }

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

  def getAllPendingSession: Future[List[ApproveSessionInfo]] = {
    val selector = BSONDocument("freeSlot" -> BSONDocument("$eq" -> false),
      "approved" -> BSONDocument("$eq" -> false),
      "decline" -> BSONDocument("$eq" -> false))

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .cursor[ApproveSessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[ApproveSessionInfo]]()))

  }

  def deleteFreeSlot(id: String): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))

    collection
      .flatMap(_.remove(selector))
  }

  def getAllFreeSlots: Future[List[ApproveSessionInfo]] = {
    val selector = BSONDocument("freeSlot" -> BSONDocument("$eq" -> true))

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .cursor[ApproveSessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[ApproveSessionInfo]]()))
  }

  def deleteFreeSlotByDate(date: BSONDateTime): Future[WriteResult] = {
    val selector = BSONDocument("date" -> date)

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .remove(selector))
  }

  def getFreeSlotByDate(date: BSONDateTime): Future[Option[ApproveSessionInfo]] = {
    val selector = BSONDocument("date" -> date)

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .cursor[ApproveSessionInfo](ReadPreference.Primary).headOption)
  }

  def swapSlot(approveSessionInfo: UpdateApproveSessionInfo, id: BSONObjectID)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> id)

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

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .update(selector, modifier))
  }

}

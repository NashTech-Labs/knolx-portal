package models

import javax.inject.Inject

import models.ApproveSessionJsonFormats._
import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
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
                              meetup: Boolean,
                              approved: Boolean = false,
                              decline: Boolean = false,
                              _id: BSONObjectID = BSONObjectID.generate
                             )

object ApproveSessionJsonFormats {

  import play.api.libs.json.Json

  implicit val approveSessionFormat = Json.format[ApproveSessionInfo]
}

class ApprovalSessionsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("approvesessions"))

  def insertSessionForApprove(approveSessionInfo: ApproveSessionInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val selector = BSONDocument("_id" -> approveSessionInfo._id.stringify)
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
          "decline" -> approveSessionInfo.decline
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

  def getAllSession(implicit ex: ExecutionContext): Future[List[ApproveSessionInfo]] = {
    collection.
      flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj()).
          cursor[ApproveSessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[ApproveSessionInfo]]()))
  }

  def getAllApprovedSession(implicit ex: ExecutionContext): Future[List[ApproveSessionInfo]] = {
    collection.
      flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj("approved" -> true)).
          cursor[ApproveSessionInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[ApproveSessionInfo]]()))
  }

  def approveSession(id: String)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "approved" -> true,
        "decline" -> false))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))

  }

  def declineSession(id: String)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "approved" -> false,
        "decline" -> true))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))

  }

}

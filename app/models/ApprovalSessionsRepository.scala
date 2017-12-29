package models

import javax.inject.Inject

import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class ApproveSessionInfo(userId: String,
                              email: String,
                              date: BSONDateTime,
                              session: String,
                              category: String,
                              subCategory: String,
                              topic: String,
                              meetup: Boolean,
                              approved: Boolean = true,
                              decline: Boolean = true,
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
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(approveSessionInfo))
  }

  def getAllSession(implicit ex: ExecutionContext): Future[List[ApproveSessionInfo]] = {
    collection.
      flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj()).
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

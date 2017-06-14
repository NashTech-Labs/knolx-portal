package models

import javax.inject.Inject

import controllers.UpdateSessionInformation
import models.SessionJsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class SessionInfo(
                        userId: String,
                        email: String,
                        date: java.util.Date,
                        session: String,
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

class SessionsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {
  val pageSize = 2
  import play.modules.reactivemongo.json._

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

  def sessions(implicit ex: ExecutionContext): Future[List[SessionInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("active" -> true))
          .sort(Json.obj("date" -> 1))
          .cursor[SessionInfo](ReadPreference.Primary)
          .collect[List]())

  def getById(id:String)(implicit ex: ExecutionContext): Future[Option[SessionInfo]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(
            BSONDocument("_id" -> BSONDocument("$oid" -> id)))
          .cursor[SessionInfo](ReadPreference.Primary)
          .headOption)

  def insert(session: SessionInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(session))

  def pageinate(pageNumber:Int)(implicit ex: ExecutionContext) : Future[List[SessionInfo]] = {
    val skipN = (pageNumber-1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)
    collection
      .flatMap(jsonCollection =>
        jsonCollection.find(Json.obj("active" -> true)).options(queryOptions).
          sort(Json.obj("date" -> 1)).
          cursor[SessionInfo](ReadPreference.Primary)
          .collect[List](pageSize))
  }

  def activeCount(implicit ex: ExecutionContext): Future[Int] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection.count(Some(Json.obj("active" -> true)))
      )

  def update(updatedRecord : UpdateSessionInformation)(implicit ex: ExecutionContext): Future[WriteResult] ={

    val selector =  BSONDocument("_id" -> BSONDocument("$oid" -> updatedRecord._id))

    val modifier = BSONDocument(
       "$set" -> BSONDocument(
        "date" -> updatedRecord.date.getTime,
        "topic" -> updatedRecord.topic,
        "session" -> updatedRecord.session,
        "meetup" ->updatedRecord.meetup)
    )

      collection.flatMap(jsonCollection =>
      jsonCollection.update(selector, modifier))
  }

}

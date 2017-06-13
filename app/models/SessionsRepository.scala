
package models

import javax.inject.Inject

import models.SessionJsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
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

  implicit val feedFormat = {
    Json.format[SessionInfo]
  }

}

class SessionsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

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

  def insert(session: SessionInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(session))

}

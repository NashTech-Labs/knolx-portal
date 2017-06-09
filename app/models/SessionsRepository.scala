package models

import javax.inject.Inject

import controllers.SessionFields._
import models.SessionJsonFormats._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class Session(userId: String,
                   email: String,
                   date: java.util.Date,
                   session: String,
                   topic: String,
                   meetup: Boolean,
                   rating: String,
                   cancelled: Boolean,
                   active: Boolean)

object SessionJsonFormats {

  import play.api.libs.json.Json

  implicit val feedFormat = Json.format[Session]
}

class SessionsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("sessions"))

  /*def delete(id: String)(implicit ex: ExecutionContext): Future[Option[JsObject]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .findAndUpdate(
            BSONDocument("_id" -> id),
            BSONDocument("$set" -> BSONDocument(Active -> false)),
            fetchNewObject = true,
            upsert = true)
          .map(_.value))*/

  def sessions(implicit ex: ExecutionContext): Future[List[Session]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj(Active -> true))
          .sort(Json.obj(Date -> 1))
          .cursor[Session](ReadPreference.Primary)
          .collect[List]())

  def insert(session: Session)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(session))

  /*def create(document: BSONDocument)(implicit ex: ExecutionContext): Future[UpdateWriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection.
          update(BSONDocument("_id" -> document.get("_id").getOrElse(BSONObjectID.generate)), document, upsert = true))*/
}

package models

import javax.inject.Inject

import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json._
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import controllers.SessionFields._

class SessionsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("sessions"))

  def delete(id: String)(implicit ex: ExecutionContext): Future[Option[JsObject]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .findAndUpdate(
            BSONDocument("_id" -> id),
            BSONDocument("$set" -> BSONDocument(Active -> false)),
            fetchNewObject = true,
            upsert = true)
          .map(_.value))

  def sessions(implicit ex: ExecutionContext): Future[List[JsObject]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj(Active -> true))
          .sort(Json.obj(Date -> 1))
          .cursor[JsObject](ReadPreference.Primary)
          .collect[List]())

  def create(document: BSONDocument)(implicit ex: ExecutionContext): Future[UpdateWriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection.
          update(BSONDocument("_id" -> document.get("_id").getOrElse(BSONObjectID.generate)), document, upsert = true))
}

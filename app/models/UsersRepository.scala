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

class UsersRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("users"))

  def getByEmail(email: String)(implicit ex: ExecutionContext): Future[List[JsObject]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("email" -> email.toLowerCase))
          .cursor[JsObject](ReadPreference.Primary)
          .collect[List]())
  }

  def create(document: BSONDocument)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .update(BSONDocument("_id" -> document.get("_id").getOrElse(BSONObjectID.generate)), document, upsert = true))
  }
}

package models

import javax.inject.Inject

import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import models.RecommendationsJsonFormats._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class RecommendationInfo(email: String,
                              recommendation: String,
                              approved: Boolean = false,
                              _id: BSONObjectID = BSONObjectID.generate())

object RecommendationsJsonFormats {

  import play.api.libs.json.Json

  implicit val recommendationsFormat = Json.format[RecommendationInfo]
}

class RecommendationsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("recommendations"))

  def insert(recommendationInfo: RecommendationInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(recommendationInfo))

  def approveRecommendation(id: String)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier = BSONDocument("$set" -> BSONDocument("approved" -> true))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def declineRecommendation(id: String)(implicit ex: ExecutionContext): Future[WriteResult] = {

    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier = BSONDocument("$set" -> BSONDocument("approved" -> false))

    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier))
  }

  def getAllRecommendations(implicit ex: ExecutionContext): Future[List[RecommendationInfo]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj()).
          cursor[RecommendationInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[RecommendationInfo]]()))
  }


  def getUserRecommendation(email: String)(implicit ex: ExecutionContext): Future[List[String]] = {
    val condition = Json.obj("email" -> email)
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition)
          .cursor[JsValue](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[JsValue]]())
      ).map(_.flatMap(_ ("recommendation").asOpt[String]))
  }

}

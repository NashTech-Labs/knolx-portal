package models

import javax.inject.Inject

import play.api.libs.json.Json
import models.RecommendationResponseJsonFormats._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class RecommendationResponseRepositoryInfo(email: String,
                                                recommendationId: String,
                                                upVote: Boolean,
                                                downVote: Boolean,
                                                _id: BSONObjectID = BSONObjectID.generate())


object RecommendationResponseJsonFormats {

  import play.api.libs.json.Json

  implicit val recommendationResponseFormat = Json.format[RecommendationResponseRepositoryInfo]
}

class RecommendationResponseRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("recommendationsresponse"))

  def upsert(recommendationResponseRepositoryInfo: RecommendationResponseRepositoryInfo): Future[UpdateWriteResult] = {
    val selector = BSONDocument("email" -> recommendationResponseRepositoryInfo.email,
      "recommendationId" -> recommendationResponseRepositoryInfo.recommendationId)

    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "upVote" -> recommendationResponseRepositoryInfo.upVote,
        "downVote" -> recommendationResponseRepositoryInfo.downVote
      )
    )
    collection
      .flatMap(jsonCollection =>
        jsonCollection.update(selector, modifier, upsert = true))
  }

  def getVote(email: String, recommendationId: String): Future[String] = {
    val selector = BSONDocument("email" -> email, "recommendationId" -> recommendationId)

    val eventualRecommendationResponse = collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(selector)
          .cursor[RecommendationResponseRepositoryInfo](ReadPreference.Primary)
          .headOption)

    eventualRecommendationResponse.map { maybeRecommendationResponse =>
      maybeRecommendationResponse.fold("") { recommendationResponse =>
        (recommendationResponse.upVote, recommendationResponse.downVote) match {
          case (true, false) => "upvote"
          case (false, true) => "downvote"
          case _             => ""
        }

      }
    }
  }
}

package models

import javax.inject.Inject

import models.FeedbackFormat._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class Question(question: String, options: List[String])

case class FeedbackForm(name: String, questions: List[Question], active: Boolean = true, _id: BSONObjectID = BSONObjectID.generate)

object FeedbackFormat {

  import play.api.libs.json.Json

  implicit val questionFormat = Json.format[Question]
  implicit val feedbackFormat = Json.format[FeedbackForm]
}

class FeedbackFormsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("feedbackforms"))

  def insert(feedbackData: FeedbackForm)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(feedbackData))


  def getAll(implicit ex: ExecutionContext): Future[List[FeedbackForm]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("active" -> true))
          .cursor[FeedbackForm](ReadPreference.primary)
          .collect[List]())
}

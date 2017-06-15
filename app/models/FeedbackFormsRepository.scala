package models

import javax.inject.Inject

import models.FeedbackFormat._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class Question(question: String, options: List[String])

case class FeedbackForm(questions: List[Question])

object FeedbackFormat {

  import play.api.libs.json.Json

  implicit val questionFormat = Json.format[Question]
  implicit val feedbackFormat = Json.format[FeedbackForm]
}

class FeedbackFormsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("feedbackforms"))

  def insert(feedbackData: FeedbackForm)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(feedbackData))

}

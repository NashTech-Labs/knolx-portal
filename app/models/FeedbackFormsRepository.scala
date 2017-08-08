package models

import javax.inject.Inject

import models.FeedbackFormat._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{QueryOpts, ReadPreference}
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONDocumentWriter, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class Question(question: String, options: List[String], questionType: String, mandatory: Boolean)

case class FeedbackForm(name: String,
                        questions: List[Question],
                        active: Boolean = true,
                        _id: BSONObjectID = BSONObjectID.generate)

object FeedbackFormat {

  import play.api.libs.json.Json

  implicit val questionFormat = Json.format[Question]
  implicit val feedbackFormat = Json.format[FeedbackForm]

  implicit object QuestionWriter extends BSONDocumentWriter[Question] {
    def write(ques: Question): BSONDocument = BSONDocument(
      "question" -> ques.question,
      "options" -> ques.options,
      "questionType" -> ques.questionType,
      "mandatory" -> ques.mandatory)
  }

}

class FeedbackFormsRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  val pageSize = 10

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("feedbackforms"))

  def insert(feedbackData: FeedbackForm)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(feedbackData))

  def update(id: String, feedbackData: FeedbackForm)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> id))
    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "name" -> feedbackData.name,
          "questions" -> feedbackData.questions,
          "active" -> feedbackData.active))

    collection.flatMap(_.update(selector, modifier))
  }

  def delete(id: String)(implicit ex: ExecutionContext): Future[Option[FeedbackForm]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .findAndUpdate(
            BSONDocument("_id" -> BSONDocument("$oid" -> id)),
            BSONDocument("$set" -> BSONDocument("active" -> false)),
            fetchNewObject = true,
            upsert = false)
          .map(_.result[FeedbackForm]))

  def getByFeedbackFormId(feedbackFormId: String): Future[Option[FeedbackForm]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj(
            "_id" -> Json.obj("$oid" -> feedbackFormId),
            "active" -> true))
          .cursor[FeedbackForm](ReadPreference.Primary)
          .headOption)

  def getAll(implicit ex: ExecutionContext): Future[List[FeedbackForm]] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("active" -> true))
          .cursor[FeedbackForm](ReadPreference.primary)
          .collect[List](-1, FailOnError[List[FeedbackForm]]()))

  def paginate(pageNumber: Int)(implicit ex: ExecutionContext): Future[List[FeedbackForm]] = {
    val skipN = (pageNumber - 1) * pageSize
    val queryOptions = new QueryOpts(skipN = skipN, batchSizeN = pageSize, flagsN = 0)

    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("active" -> true))
          .options(queryOptions)
          .sort(Json.obj("name" -> 1))
          .cursor[FeedbackForm](ReadPreference.Primary)
          .collect[List](pageSize, FailOnError[List[FeedbackForm]]()))
  }

  def activeCount(implicit ex: ExecutionContext): Future[Int] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection.count(Some(Json.obj("active" -> true))))

}

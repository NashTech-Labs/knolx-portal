package models

import java.util.Date

import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import scala.concurrent.ExecutionContext.Implicits.global

class FeedbackFormsResponseRepositorySpec extends PlaySpecification {

  val feedbackFormsResponseRepository = new FeedbackFormsResponseRepository(TestDb.reactiveMongoApi)

  val feedbackFormResponseId = BSONObjectID.generate

  private val nowMillis = System.currentTimeMillis
  private val date = new Date(nowMillis)

  "Feedback forms reponse repository" should {

    "store a feedback form response" in {
      val questionsResponse = List(QuestionResponse("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "1"))
      val feedbackFormResponse = FeedbackFormsResponse("test@example.com", "userId", "sessionId", "feedbackFormId", "formName",
        questionsResponse, BSONDateTime(date.getTime), feedbackFormResponseId)

      val created = await(feedbackFormsResponseRepository.insert(feedbackFormResponse).map(_.ok))

      created must beEqualTo(true)
    }

    "update feedback form response" in {
      val questionsResponse = List(QuestionResponse("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "1"))
      val feedbackFormResponse = FeedbackFormsResponse("test@example.com", "userId", "sessionId", "feedbackFormId", "formName",
        questionsResponse, BSONDateTime(date.getTime), feedbackFormResponseId)

      val updated = await(feedbackFormsResponseRepository.update(feedbackFormResponseId.stringify, feedbackFormResponse).map(_.ok))

      updated must beEqualTo(true)
    }
  }

}

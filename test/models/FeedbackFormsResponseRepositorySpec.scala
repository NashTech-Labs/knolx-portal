package models

import java.text.SimpleDateFormat

import play.api.test.PlaySpecification
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FeedbackFormsResponseRepositorySpec extends PlaySpecification {

  val feedbackFormsResponseRepository = new FeedbackFormsResponseRepository(TestDb.reactiveMongoApi)
  val writeResult: Future[DefaultWriteResult] = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val questionResponseInformation = QuestionResponse("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "2")
  private val feedbackResponse = FeedbackFormsResponse("test@example.com",
    "presenter@example.com",
    _id.stringify, _id.stringify,
    "topic",
    meetup = false,
    BSONDateTime(date.getTime),
    "session1",
    List(questionResponseInformation),
    BSONDateTime(date.getTime),
    _id)

  "Feedback forms response repository" should {

    "insert response if not found, else update it" in {
      val inserted = await(feedbackFormsResponseRepository.upsert(feedbackResponse).map(_.ok))

      inserted must beEqualTo(true)
    }

    "fetch response by userId and sessionId" in {
      val response = await(feedbackFormsResponseRepository.getByUsersSession(_id.stringify, _id.stringify))
      response.get.feedbackResponse must beEqualTo(List(questionResponseInformation))
    }

    "fetch all responses for a particular user and session" in {
      val response = await(feedbackFormsResponseRepository.allResponsesBySession("presenter@example.com", _id.stringify))
      response.size must beEqualTo(1)
      response.head.feedbackResponse must beEqualTo(List(questionResponseInformation))
    }

  }

}

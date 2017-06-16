package models

import play.api.test.PlaySpecification
import scala.concurrent.ExecutionContext.Implicits.global

class FeedbackFormsRepositorySpec extends PlaySpecification {

  val feedbackFormsRepository = new FeedbackFormsRepository(TestDb.reactiveMongoApi)

  "Feedback forms repository" should {

    "create a new feedback form" in {
      val questions = List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5")))
      val feedbackForm = FeedbackForm("form name", questions)

      val created = await(feedbackFormsRepository.insert(feedbackForm).map(_.ok))

      created must beEqualTo(true)
    }

  }

}

package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID
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

    "get all feedback forms" in {
      val forms = await(feedbackFormsRepository.getAll)

      forms.map(_.name) must contain("form name")
      forms.flatMap(_.questions) must contain(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5")))
    }

    "get feedback forms in paginated format" in {
      val paginatedForms = await(feedbackFormsRepository.paginate(1))

      paginatedForms.map(_.name) must contain("form name")
      paginatedForms.flatMap(_.questions) must contain(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5")))
    }

    "get count of all active forms" in {
      val activeForms = await(feedbackFormsRepository.activeCount)

      activeForms must beEqualTo(1)
    }

    "delete feedback form" in {
      val formId = await(feedbackFormsRepository.getAll).head._id

      val deleted = await(feedbackFormsRepository.delete(formId.stringify))

      deleted.isDefined must beEqualTo(true)
    }

  }

}

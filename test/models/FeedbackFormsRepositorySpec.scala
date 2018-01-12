package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

class FeedbackFormsRepositorySpec extends PlaySpecification {

  val feedbackFormsRepository = new FeedbackFormsRepository(TestDb.reactiveMongoApi)

  val feedbackFormId = BSONObjectID.generate

  "Feedback forms repository" should {

    "create a new feedback form" in {
      val questions = List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true))
      val feedbackForm = FeedbackForm("Feedback Form Template 1", questions, active = true, feedbackFormId)

      val created = await(feedbackFormsRepository.insert(feedbackForm).map(_.ok))

      created must beEqualTo(true)
    }

    "get all feedback forms" in {
      val forms = await(feedbackFormsRepository.getAll)

      forms.map(_.name) must contain("Feedback Form Template 1")
      forms.flatMap(_.questions) must contain(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true))
    }

    "get feedback forms in paginated format" in {
      val paginatedForms = await(feedbackFormsRepository.paginate(1))

      paginatedForms.map(_.name) must contain("Feedback Form Template 1")
      paginatedForms.flatMap(_.questions) must contain(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true))
    }

    "get count of all active forms" in {
      val activeForms = await(feedbackFormsRepository.activeCount)

      activeForms must beEqualTo(1)
    }

    "get feedback form by id" in {
      val feedbackForm = await(feedbackFormsRepository.getByFeedbackFormId(feedbackFormId.stringify))

      val questions = List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true))
      val expectedFeedbackForm = FeedbackForm("Feedback Form Template 1", questions, active = true, feedbackFormId)

      expectedFeedbackForm must beEqualTo(FeedbackForm("Feedback Form Template 1", questions, active = true, feedbackFormId))
    }

    "delete feedback form" in {
      val formId = await(feedbackFormsRepository.getAll).head._id

      val deleted = await(feedbackFormsRepository.delete(formId.stringify))

      deleted.isDefined must beEqualTo(true)
    }

    "getByEmail feedback form" in {
      val questions = List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true))
      val feedbackForm = FeedbackForm("Feedback Form Template 1", questions, active = true, feedbackFormId)

      val updated = await(feedbackFormsRepository.update(feedbackFormId.stringify, feedbackForm).map(_.ok))

      updated must beEqualTo(true)
    }

  }

}

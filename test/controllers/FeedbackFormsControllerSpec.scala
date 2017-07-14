package controllers

import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, _}
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.BSONObjectID
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FeedbackFormsControllerSpec extends PlaySpecification with TestEnvironment {

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val emailObject = Future.successful(List(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))
  private val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"))),
    active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp

    val mailerClient = mock[MailerClient]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val sessionsRepository = mock[SessionsRepository]

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }

    lazy val controller =
      new FeedbackFormsController(
        knolxControllerComponent.messagesApi,
        mailerClient,
        usersRepository,
        feedbackFormsRepository,
        sessionsRepository,
        dateTimeUtility,
        knolxControllerComponent)
  }

  "Feedback controller" should {

    "create render feedback form page" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.feedbackForm()(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
      contentAsString(response) must contain("""form-name""")
    }

    "create feedback form" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"How good is knolx portal?","options":["1","2","3","4","5"]}]}"""

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val questions = List(Question("How good is knolx portal?", List("1", "2", "3", "4", "5")))
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.insert(any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo OK
      contentAsString(response) must be equalTo "Feedback form successfully created!"
    }

    "not create feedback form because feedback form is not inserted in database" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"How good is knolx portal?","options":["1","2","3","4","5"]}]}"""

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val questions = List(Question("How good is knolx portal?", List("1", "2", "3", "4", "5")))
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.insert(any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo INTERNAL_SERVER_ERROR
      contentAsString(response) must be equalTo "Something went wrong!"
    }

    "not create feedback form because of malformed data" in new WithTestApplication {
      val payload = """[{"questions":"","options":["1","2","3","4","5"]}]"""

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Malformed data!"
    }

    "not create feedback form because name is empty" in new WithTestApplication {
      val payload = """{"name":"","questions":[{"question":"","options":["1","2","3","4","5"]}]}"""

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Form name must not be empty!"
    }

    "not create feedback form because question value is empty" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"","options":["1","2","3","4","5"]}]}"""

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must not be empty!"
    }

    "not create feedback form because options value is empty" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"How good is knolx portal?","options":["","2","3","4","5"]}]}"""

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Options must not be empty!"
    }

    "not create feedback form because options are not present" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"How good is knolx portal?","options":[]}]}"""

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must require at least 1 option!"
    }

    "render manage feedback forms page" in new WithTestApplication {
      val feedbackForms = List(FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"))),
        active = true, BSONObjectID.parse("5943cdd60900000900409b26").get))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.paginate(1) returns Future.successful(feedbackForms)
      feedbackFormsRepository.activeCount returns Future.successful(1)

      val response = controller.manageFeedbackForm(1)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
      contentAsString(response) must contain("""feedback-div-outer""")
    }

    "delete feedback form" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"))),
        active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.delete("5943cdd60900000900409b26") returns Future.successful(Some(feedbackForms))

      val response = controller.deleteFeedbackForm("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo SEE_OTHER
    }

    "not delete feedback form because of some error at database layer" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.delete("5943cdd60900000900409b26") returns Future.successful(None)

      val response = controller.deleteFeedbackForm("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo SEE_OTHER
    }

    "send form asked to update to feedback update page" in new WithTestApplication {

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.getByFeedbackFormId("5943cdd60900000900409b26") returns Future.successful(Some(feedbackForms))

      val response = controller.update("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "not update Feedback Form as not found" in new WithTestApplication {

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.getByFeedbackFormId("5943cdd60900000900409b26") returns Future.successful(None)

      val response = controller.update("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo SEE_OTHER
    }

    "update feedback form" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.update(any[String], any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val request =
        FakeRequest(POST, "/feedbackform/update")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"title","questions":[{"question":"question?","options":["option","option"]}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo OK
      contentAsString(response) must be equalTo "Feedback form successfully updated!"
    }

    "not update feedback form due to malformed data" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val request =
        FakeRequest(POST, "/feedbackform/update")
          .withBody(Json.parse(
            """[{"question":"question?","options":["option","option"]}]""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Malformed data!"
    }

    "not update feedback form due to malformed data with options missing" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val request =
        FakeRequest(POST, "/feedbackform/update")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"test","questions":[{"question":"question?","options":[]}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must require at least 1 option!"
    }

    "not update feedback form due to malformed data when name is empty" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val request =
        FakeRequest(POST, "/feedbackform/update")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"","questions":[{"question":"question?","options":["option","option"]}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Form name must not be empty!"
    }

    "not update feedback form due to malformed data when question is empty" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val request =
        FakeRequest(POST, "/feedbackform/update")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"title","questions":[{"question":"","options":["option","option"]}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must not be empty!"
    }

    "not update feedback form due to malformed data when option value is empty" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val request =
        FakeRequest(POST, "/feedbackform/update")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"title","questions":[{"question":"","options":["","option"]}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Options must not be empty!"
    }

    "not update feedback form and yield internal server error" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.update(any[String], any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val request =
        FakeRequest(POST, "/feedbackform/update")
          .withBody(Json.parse(
            """{"id":"","name":"title","questions":[{"question":"question?","options":["option","option"]}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)
      status(response) must be equalTo INTERNAL_SERVER_ERROR
      contentAsString(response) must be equalTo "Something went wrong!"
    }

    "build json from case class" in new WithTestApplication {
      val questions = Question("how is knolx portal?", List("awesome", "i can do it better"))
      val feedbackForm = FeedbackForm("test", List(questions), active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)

      val result = controller.jsonCountBuilder(feedbackForm)

      result must be equalTo """{"0":"2"}"""
    }

    "get feedback form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      feedbackFormsRepository.getByFeedbackFormId("5943cdd60900000900409b26") returns Future.successful(Some(feedbackForms))

      val request =
        FakeRequest(GET, "/feedbackform/preview?id=5943cdd60900000900409b26")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.getFeedbackFormPreview("5943cdd60900000900409b26")(request)
      status(response) must be equalTo OK
    }

    "not get feedback form because form by id does not exist" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"))),
        active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)

      feedbackFormsRepository.getByFeedbackFormId("5943cdd60900000900409b26") returns Future.successful(None)

      val request =
        FakeRequest(GET, "/feedbackform/preview?id=5943cdd60900000900409b26")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.getFeedbackFormPreview("5943cdd60900000900409b26")(request)
      status(response) must be equalTo NOT_FOUND
    }

  }

}

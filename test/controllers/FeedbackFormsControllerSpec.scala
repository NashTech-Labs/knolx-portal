package controllers

import java.text.SimpleDateFormat

import com.google.inject.AbstractModule
import com.typesafe.config.ConfigFactory
import helpers.TestHelpers
import models._
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.Configuration
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, _}
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FeedbackFormsControllerSpec extends PlaySpecification with Mockito {

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val emailObject = Future.successful(Some(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))
  private val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)),
    active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category", "subCategory", "feedbackFormId", "topic",
      1, meetup = true, "rating", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), temporaryYoutubeURL = None, reminder = false, notification = false, _id)))

  trait TestScope extends Scope {
    val mailerClient = mock[MailerClient]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val sessionsRepository = mock[SessionsRepository]
    val usersRepository = mock[UsersRepository]

    val config = Configuration(ConfigFactory.load("application.conf"))

    val knolxControllerComponent = TestHelpers.stubControllerComponents(usersRepository, config)

    val testModule = Option(new AbstractModule with AkkaGuiceSupport {
      override def configure(): Unit = {
        bind(classOf[KnolxControllerComponents])
          .toInstance(knolxControllerComponent)
      }
    })

    lazy val controller =
      new FeedbackFormsController(
        TestHelpers.stubMessagesApi(),
        mailerClient,
        feedbackFormsRepository,
        sessionsRepository,
        dateTimeUtility,
        knolxControllerComponent)
  }

  "Feedback controller" should {

    "create render feedback form page" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.feedbackForm()(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
      contentAsString(response) must contain("""form-name""")
    }

    "create feedback form" in new TestScope {
      val payload =
        """{"name":"Test Form","questions":
          |[{"question":"How good is knolx portal?","options":
          |["1","2","3","4","5"],"questionType":"MCQ","mandatory":true}]}""".stripMargin

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val questions = List(Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true))
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.insert(any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo OK
      contentAsString(response) must be equalTo "Feedback form successfully created!"
    }

    "not create feedback form because feedback form is not inserted in database" in new TestScope {
      val payload =
        """{"name":"Test Form","questions":[{"question":
          |"How good is knolx portal?","options":["1","2","3","4","5"],
          |"questionType":"MCQ","mandatory":true}]}""".stripMargin

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val questions = List(Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true))
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.insert(any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo INTERNAL_SERVER_ERROR
      contentAsString(response) must be equalTo "Something went wrong!"
    }

    "not create feedback form because of malformed data" in new TestScope {
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

    "not create feedback form because name is empty" in new TestScope {
      val payload =
        """{"name":"","questions":
          |[{"question":"","options":["1","2","3","4","5"]}]}""".stripMargin

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

    "not create feedback form because question value is empty" in new TestScope {
      val payload =
        """{"name":"Test Form","questions":
          |[{"question":"","options":["1","2","3","4","5"],
          |"questionType":"MCQ","mandatory":true}]}""".stripMargin

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

    "not create feedback form because options value is empty" in new TestScope {
      val payload =
        """{"name":"Test Form","questions":
          |[{"question":"How good is knolx portal?","options":
          |["","2","3","4","5"],"questionType":"MCQ","mandatory":true}]}""".stripMargin

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

    "not create feedback form because options are not present" in new TestScope {
      val payload =
        """{"name":"Test Form","questions":
          |[{"question":"How good is knolx portal?","options":[],
          |"questionType":"MCQ","mandatory":true}]}""".stripMargin

      val request =
        FakeRequest(POST, "/feedbackform/create")
          .withBody(Json.parse(payload))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must require at least 1 option besides Did not attend!"
    }

    "render manage feedback forms page" in new TestScope {
      val feedbackForms = List(FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)),
        active = true, BSONObjectID.parse("5943cdd60900000900409b26").get))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.paginate(1) returns Future.successful(feedbackForms)
      feedbackFormsRepository.activeCount returns Future.successful(1)

      val response = controller.manageFeedbackForm(1)(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
      contentAsString(response) must contain("""feedback-div-outer""")
    }

    "delete feedback form" in new TestScope {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)),
        active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.delete("5943cdd60900000900409b26") returns Future.successful(Some(feedbackForms))

      val response = controller.deleteFeedbackForm("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo SEE_OTHER
    }

    "not delete feedback form because of some error at database layer" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject
      feedbackFormsRepository.delete("5943cdd60900000900409b26") returns Future.successful(None)

      val response = controller.deleteFeedbackForm("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo SEE_OTHER
    }

    "render feedback form getByEmail page" in new TestScope {
      sessionsRepository.activeSessions() returns sessionObject
      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.getByFeedbackFormId("5943cdd60900000900409b26") returns Future.successful(Some(feedbackForms))

      val response = controller.update("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "not getByEmail Feedback Form as not found" in new TestScope {
      sessionsRepository.activeSessions() returns sessionObject
      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.getByFeedbackFormId("5943cdd60900000900409b26") returns Future.successful(None)

      val response = controller.update("5943cdd60900000900409b26")(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo SEE_OTHER
    }

    "getByEmail feedback form" in new TestScope {
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.update(any[String], any[FeedbackForm])(any[ExecutionContext]) returns writeResult
      sessionsRepository.activeSessions() returns sessionObject

      val request =
        FakeRequest(POST, "/feedbackform/getByEmail")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"title","questions":
              |[{"question":"question?","options":["option","option"],
              |"questionType":"MCQ","mandatory":true}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo OK
      contentAsString(response) must be equalTo "Feedback form successfully updated!"
    }

    "not getByEmail feedback form due to malformed data" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val request =
        FakeRequest(POST, "/feedbackform/getByEmail")
          .withBody(Json.parse(
            """[{"question":"question?","options":["option","option"]}]""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Malformed data!"
    }

    "not getByEmail feedback form due to malformed data with options missing" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject

      val request =
        FakeRequest(POST, "/feedbackform/getByEmail")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"test","questions":
              |[{"question":"question?","options":[],
              |"questionType":"MCQ","mandatory":true}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must require at least 1 option besides Did not attend!"
    }

    "not getByEmail feedback form due to malformed data when name is empty" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject

      val request =
        FakeRequest(POST, "/feedbackform/getByEmail")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"","questions":
              |[{"question":"question?","options":["option","option"],
              |"questionType":"MCQ","mandatory":true}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Form name must not be empty!"
    }

    "not getByEmail feedback form due to malformed data when question is empty" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject

      val request =
        FakeRequest(POST, "/feedbackform/getByEmail")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"title","questions":
              |[{"question":"","options":["option","option"],"questionType":
              |"MCQ","mandatory":true}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must not be empty!"
    }

    "not getByEmail feedback form due to malformed data when option value is empty" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject

      val request =
        FakeRequest(POST, "/feedbackform/getByEmail")
          .withBody(Json.parse(
            """{"id":"5943cdd60900000900409b26","name":"title","questions":
              |[{"question":"","options":["","option"],
              |"questionType":"MCQ","mandatory":true}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Options must not be empty!"
    }

    "not getByEmail feedback form and yield internal server error" in new TestScope {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.update(any[String], any[FeedbackForm])(any[ExecutionContext]) returns writeResult
      sessionsRepository.activeSessions() returns sessionObject

      val request =
        FakeRequest(POST, "/feedbackform/getByEmail")
          .withBody(Json.parse(
            """{"id":"","name":"title","questions":
              |[{"question":"question?","options":
              |["option","option"],"questionType":"MCQ","mandatory":true}]}""".stripMargin))
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.updateFeedbackForm()(request)
      status(response) must be equalTo INTERNAL_SERVER_ERROR
      contentAsString(response) must be equalTo "Something went wrong!"
    }

    "get feedback form" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject

      feedbackFormsRepository.getByFeedbackFormId("5943cdd60900000900409b26") returns Future.successful(Some(feedbackForms))

      val request =
        FakeRequest(GET, "/feedbackform/preview?id=5943cdd60900000900409b26")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken

      val response = controller.getFeedbackFormPreview("5943cdd60900000900409b26")(request)
      status(response) must be equalTo OK
    }

    "not get feedback form because form by id does not exist" in new TestScope {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)),
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

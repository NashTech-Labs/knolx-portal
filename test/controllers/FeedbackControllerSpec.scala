package controllers

import models._
import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.test.{WithApplication, FakeRequest, PlaySpecification}
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{ExecutionContext, Future}

class FeedbackControllerSpec extends PlaySpecification with Mockito {

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val emailObject = Future.successful(List(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

  "Feedback controller" should {

    "create render feedback form page" in new WithApplication {
      val feedbackFormsController = testObject

      feedbackFormsController.usersRepository.getByEmail("test@example.com") returns emailObject

      val response = feedbackFormsController.controller.feedbackForm()(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
      contentAsString(response) must contain("""<title>Create Feedback Form</title>""")
    }

    "create feedback form" in new WithApplication {
      val feedbackFormsController = testObject

      val payload = """[{"question":"How good is knolx portal?","options":["1","2","3","4","5"]}]"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      val questions = List(Question("How good is knolx portal?", List("1", "2", "3", "4", "5")))
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      feedbackFormsController.usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsController.feedbackRepository.insert(any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val response = feedbackFormsController.controller.createFeedbackForm()(request)

      status(response) must be equalTo OK
      contentAsString(response) must be equalTo "Feedback form successfully created!"
    }

    "not create feedback form because feedback form is not inserted in database" in new WithApplication {
      val feedbackFormsController = testObject

      val payload = """[{"question":"How good is knolx portal?","options":["1","2","3","4","5"]}]"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      val questions = List(Question("How good is knolx portal?", List("1", "2", "3", "4", "5")))
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      feedbackFormsController.usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsController.feedbackRepository.insert(any[FeedbackForm])(any[ExecutionContext]) returns writeResult

      val response = feedbackFormsController.controller.createFeedbackForm()(request)

      status(response) must be equalTo INTERNAL_SERVER_ERROR
      contentAsString(response) must be equalTo "Something went wrong!"
    }

    "not create feedback form because of malformed data" in new WithApplication {
      val feedbackFormsController = testObject

      val payload = """[{"questions":"","options":["1","2","3","4","5"]}]"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      feedbackFormsController.usersRepository.getByEmail("test@example.com") returns emailObject

      val response = feedbackFormsController.controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Malformed data!"
    }

    "not create feedback form because question value is empty" in new WithApplication {
      val feedbackFormsController = testObject

      val payload = """[{"question":"","options":["1","2","3","4","5"]}]"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      feedbackFormsController.usersRepository.getByEmail("test@example.com") returns emailObject

      val response = feedbackFormsController.controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must not be empty!"
    }

    "not create feedback form because options value is empty" in new WithApplication {
      val feedbackFormsController = testObject

      val payload = """[{"question":"How good is the knolx portal ?","options":["","2","3","4","5"]}]"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      feedbackFormsController.usersRepository.getByEmail("test@example.com") returns emailObject

      val response = feedbackFormsController.controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Options must not be empty!"
    }

    "not create feedback form because options are not present" in new WithApplication {
      val feedbackFormsController = testObject

      val payload = """[{"question":"How good is the knolx portal ?","options":[]}]"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      feedbackFormsController.usersRepository.getByEmail("test@example.com") returns emailObject

      val response = feedbackFormsController.controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must require at least 1 option!"
    }
  }

  def testObject: TestObject = {
    val mockedMailerClient = mock[MailerClient]
    val mockedUsersRepository: UsersRepository = mock[UsersRepository]
    val mockedFeedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]

    val controller = new FeedbackFormsController(mockedMailerClient, mockedUsersRepository, mockedFeedbackFormsRepository)

    TestObject(mockedMailerClient, mockedUsersRepository, mockedFeedbackFormsRepository, controller)
  }

  case class TestObject(mailerClient: MailerClient,
                        usersRepository: UsersRepository,
                        feedbackRepository: FeedbackFormsRepository,
                        controller: FeedbackFormsController)

}

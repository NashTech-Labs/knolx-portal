package controllers

import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.ShouldThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.test.{FakeRequest, Helpers, _}
import play.api.{Application, Configuration, Environment}
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FeedbackFormsControllerSpec extends PlaySpecification with Mockito {

  abstract class WithTestApplication(val app: Application = GuiceApplicationBuilder().build()) extends Around with Scope with ShouldThrownExpectations with Mockito {
    implicit def implicitApp: play.api.Application = app

    implicit def implicitMaterializer: Materializer = app.materializer

    def this(builder: GuiceApplicationBuilder => GuiceApplicationBuilder) = this(builder(GuiceApplicationBuilder()).build())

    override def around[T: AsResult](t: => T): Result = Helpers.running(app)(AsResult.effectively(t))

    val mailerClient = mock[MailerClient]
    val usersRepository: UsersRepository = mock[UsersRepository]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]

    val config = Configuration(ConfigFactory.load("application.conf"))
    val messages = new DefaultMessagesApi(Environment.simple(), config, new DefaultLangs(config))

    val controller = new FeedbackFormsController(messages, mailerClient, usersRepository, feedbackFormsRepository)
  }

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val emailObject = Future.successful(List(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

  "Feedback controller" should {

    "create render feedback form page" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.feedbackForm()(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
      contentAsString(response) must contain("""<title>Create Feedback Form</title>""")
    }

    "create feedback form" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"How good is knolx portal?","options":["1","2","3","4","5"]}]}"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

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

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

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

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Malformed data!"
    }

    "not create feedback form because question value is empty" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"","options":["1","2","3","4","5"]}]}"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Question must not be empty!"
    }

    "not create feedback form because options value is empty" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"How good is knolx portal?","options":["","2","3","4","5"]}]}"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.createFeedbackForm()(request)

      status(response) must be equalTo BAD_REQUEST
      contentAsString(response) must be equalTo "Options must not be empty!"
    }

    "not create feedback form because options are not present" in new WithTestApplication {
      val payload = """{"name":"Test Form","questions":[{"question":"How good is knolx portal?","options":[]}]}"""

      val request = FakeRequest(POST, "/feedbackform/create").withBody(Json.parse(payload))
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

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

      val response = controller.manageFeedbackForm(1)(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
      contentAsString(response) must contain("""<td>form name</td>""")
    }

    "delete feedback form" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"))),
        active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.delete("5943cdd60900000900409b26") returns Future.successful(Some(feedbackForms))

      val response = controller.delete("5943cdd60900000900409b26")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo SEE_OTHER
    }

    "not delete feedback form because of some error at database layer" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.delete("5943cdd60900000900409b26") returns Future.successful(None)

      val response = controller.delete("5943cdd60900000900409b26")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo SEE_OTHER
    }
  }

}

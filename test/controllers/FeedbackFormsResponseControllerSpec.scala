package controllers

import java.text.SimpleDateFormat
import java.time.ZoneId

import com.typesafe.config.ConfigFactory
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.ShouldThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.libs.mailer.MailerClient
import play.api.test.{FakeRequest, _}
import play.api.{Application, Configuration, Environment}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FeedbackFormsResponseControllerSpec extends PlaySpecification with TestEnvironment {
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "feedbackFormId", "topic",
      1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))
  private val emailObject = Future.successful(List(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))
  private val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"))),
    active = true, BSONObjectID.parse("5943cdd60900000900409b26").get)

  abstract class WithTestApplication(val app: Application = fakeApp) extends Around
    with Scope with ShouldThrownExpectations with Mockito {

    val mailerClient = mock[MailerClient]
    val usersRepository: UsersRepository = mock[UsersRepository]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val sessionsRepository = mock[SessionsRepository]

    val config = Configuration(ConfigFactory.load("application.conf"))
    val messages = new DefaultMessagesApi(Environment.simple(), config, new DefaultLangs(config))

    val controller = new FeedbackFormsResponseController(messages, mailerClient, usersRepository, feedbackFormsRepository, sessionsRepository, dateTimeUtility)

    override def around[T: AsResult](t: => T): Result = Helpers.running(app)(AsResult.effectively(t))
  }

  "Feedback Response Controller" should {

    "not render feedback form for today if session associated feedback form not found" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getSessionsTillNow returns sessionObject
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(None)
      dateTimeUtility.ISTZoneId returns ZoneId.of("Asia/Calcutta")
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form found and session not expired" in new WithTestApplication {
      val sessionObjectWithCurrentDate =
        Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(System.currentTimeMillis), "sessions", "feedbackFormId", "topic",
          1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getSessionsTillNow returns sessionObjectWithCurrentDate
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))
      dateTimeUtility.ISTZoneId returns ZoneId.of("Asia/Calcutta")
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form found and session expired" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getSessionsTillNow returns sessionObject
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))
      dateTimeUtility.ISTZoneId returns ZoneId.of("Asia/Calcutta")
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
    }

  }

}

package controllers

import java.text.SimpleDateFormat

import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.libs.mailer.MailerClient
import play.api.mvc.Results
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, _}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FeedbackFormsResponseControllerSpec extends PlaySpecification with Results {

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "feedbackFormId", "topic",
      1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))
  private val noActiveSessionObject = Future.successful(Nil)
  private val emailObject = Future.successful(Some(UserInfo("test@example.com",
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
      new FeedbackFormsResponseController(
        knolxControllerComponent.messagesApi,
        mailerClient,
        usersRepository,
        feedbackFormsRepository,
        sessionsRepository,
        dateTimeUtility,
        knolxControllerComponent)
  }

  "Feedback Response Controller" should {

    "not render feedback form for today if session associated feedback form not found" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions returns sessionObject
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(None)

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form exists and session has not expired" in new WithTestApplication {
      val sessionObjectWithCurrentDate =
        Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(System.currentTimeMillis), "sessions", "feedbackFormId", "topic",
          1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions returns sessionObjectWithCurrentDate
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form exists and session has expired expired" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions returns sessionObject
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today with immidiate expored sessions if no active sessions found" in new WithTestApplication {

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions returns noActiveSessionObject
      sessionsRepository.immediatePreviousExpiredSessions returns sessionObject

      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK
    }

  }

}

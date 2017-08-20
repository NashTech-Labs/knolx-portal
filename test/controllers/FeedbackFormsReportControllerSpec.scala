package controllers

import java.text.SimpleDateFormat

import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.libs.mailer.MailerClient
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, PlaySpecification}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FeedbackFormsReportControllerSpec extends PlaySpecification with TestEnvironment {

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val emailObject = Future.successful(Some(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, BSONDateTime(date.getTime), 0, _id)))
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "feedbackFormId", "topic",
      1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))
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

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()
    lazy val controller =
      new FeedbackFormsReportController(
        knolxControllerComponent.messagesApi,
        mailerClient,
        usersRepository,
        feedbackFormsRepository,
        feedbackFormsResponseRepository,
        sessionsRepository,
        dateTimeUtility,
        knolxControllerComponent)
    val mailerClient = mock[MailerClient]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]
    val feedbackFormsResponseRepository: FeedbackFormsResponseRepository = mock[FeedbackFormsResponseRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val sessionsRepository = mock[SessionsRepository]

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Feedback forms report controller" should {

    "render reports page for a particular user if user has active sessions and also has feedbacks" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@example.com")) returns sessionObject
      sessionsRepository.userSessionsTillNow("test@example.com") returns sessionObject


      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render reports page for a particular user if user no active sessions and also no feedbacks" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@example.com")) returns Future.successful(List())
      sessionsRepository.userSessionsTillNow("test@example.com") returns Future.successful(List())

      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render reports page for a particular user if user has active sessions with no feedback submitted yet" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@example.com")) returns sessionObject
      sessionsRepository.userSessionsTillNow("test@example.com") returns sessionObject.map(_.map(_.copy(_id = BSONObjectID.generate())))


      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render reports page for a particular user if  no session is  active for the user but has feedback form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@example.com")) returns Future.successful(List())
      sessionsRepository.userSessionsTillNow("test@example.com") returns sessionObject

      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }


    "render reports page for a particular user if user has active session and has no feedback form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@example.com")) returns sessionObject
      sessionsRepository.userSessionsTillNow("test@example.com") returns Future.successful(List())

      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render report by session id if responses found" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      feedbackFormsResponseRepository.allResponsesBySession("test@example.com", _id.stringify) returns Future.successful(List(feedbackResponse))

      val response = controller.fetchAllResponsesBySessionId(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render report by session id if no response found" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      feedbackFormsResponseRepository.allResponsesBySession("test@example.com", _id.stringify) returns Future.successful(List())

      val response = controller.fetchAllResponsesBySessionId(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

  }

}

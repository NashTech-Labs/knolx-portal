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
import play.api.libs.mailer.MailerClient
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, PlaySpecification}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FeedbackFormsReportControllerSpec extends PlaySpecification with Mockito {

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val emailObject = Future.successful(Some(UserInfo("test@knoldus.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category", "subCategory", "feedbackFormId", "topic",
      1, meetup = true, "rating", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, _id)))
  private val optionOfSessionObject =
    Future.successful(Some(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category", "subCategory", "feedbackFormId", "topic",
      1, meetup = true, "rating", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, _id)))
  private val questionResponseInformation = QuestionResponse("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "2")
  private val feedbackResponse = FeedbackFormsResponse("test@knoldus.com",false,
    "presenter@example.com",
    _id.stringify, _id.stringify,
    "topic",
    meetup = false,
    BSONDateTime(date.getTime),
    "session1",
    List(questionResponseInformation),
    BSONDateTime(date.getTime),
    0.00,
    _id)

  trait TestScope extends Scope {
    val mailerClient = mock[MailerClient]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val feedbackFormsResponseRepository = mock[FeedbackFormsResponseRepository]
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
      new FeedbackFormsReportController(
        TestHelpers.stubMessagesApi(),
        mailerClient,
        usersRepository,
        feedbackFormsRepository,
        feedbackFormsResponseRepository,
        sessionsRepository,
        dateTimeUtility,
        knolxControllerComponent)
  }

  "Feedback forms report controller" should {

    "render reports page for a particular user if user has active sessions and also has feedbacks" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@knoldus.com")) returns sessionObject
      sessionsRepository.userSessionsTillNow(Some("test@knoldus.com")) returns sessionObject


      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render reports page with all users report, if user is admin" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.activeSessions(None) returns sessionObject
      sessionsRepository.userSessionsTillNow(None) returns sessionObject


      val response = controller.renderAllFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render reports page for a particular user if user no active sessions and also no feedbacks" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@knoldus.com")) returns Future.successful(List())
      sessionsRepository.userSessionsTillNow(Some("test@knoldus.com")) returns Future.successful(List())

      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render reports page for a particular user if user has active sessions with no feedback submitted yet" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@knoldus.com")) returns sessionObject
      sessionsRepository.userSessionsTillNow(Some("test@knoldus.com")) returns sessionObject.map(_.map(_.copy(_id = BSONObjectID.generate())))


      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render reports page for a particular user if  no session is  active for the user but has feedback form" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@knoldus.com")) returns Future.successful(List())
      sessionsRepository.userSessionsTillNow(Some("test@knoldus.com")) returns sessionObject

      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }


    "render reports page for a particular user if user has active session and has no feedback form" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.activeSessions(Some("test@knoldus.com")) returns sessionObject
      sessionsRepository.userSessionsTillNow(Some("test@knoldus.com")) returns Future.successful(List())

      val response = controller.renderUserFeedbackReports()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render report by session id if responses found for admin" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify)  returns optionOfSessionObject
      feedbackFormsResponseRepository.allResponsesBySession(_id.stringify, None) returns Future.successful(List(feedbackResponse))

      val response = controller.fetchAllResponsesBySessionId(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render report by session id if responses found for user" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject.map(user => Some(user.get.copy(admin = false)))
      sessionsRepository.getById(_id.stringify)  returns optionOfSessionObject
      feedbackFormsResponseRepository.allResponsesBySession(_id.stringify, Some("test@knoldus.com")) returns Future.successful(List(feedbackResponse))

      val response = controller.fetchUserResponsesBySessionId(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render report by session id if no response found" in new TestScope {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify)  returns optionOfSessionObject
      feedbackFormsResponseRepository.allResponsesBySession(_id.stringify, None) returns Future.successful(List())

      val response = controller.fetchAllResponsesBySessionId(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }
  }

}

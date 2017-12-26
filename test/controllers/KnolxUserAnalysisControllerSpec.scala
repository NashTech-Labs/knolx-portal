package controllers

import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId}
import java.util.TimeZone

import helpers.TestEnvironment
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.mvc.Results
import play.api.test.{FakeRequest, PlaySpecification}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import play.api.test.CSRFTokenHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class KnolxUserAnalysisControllerSpec extends PlaySpecification with Results {

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val emailObject = Future.successful(Some(UserInfo("test@knoldus.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true,
    coreMember = false, superUser = true, BSONDateTime(date.getTime), 0, _id)))

  private val sessionObject = Future.successful(List(SessionInfo(_id.stringify, "test@knoldus.com",
    BSONDateTime(date.getTime), "sessions", "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true,
    "rating", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtubeURL"),
    Some("slideShareURL"), temporaryYoutubeURL = Some("temporary/youtube/url"), reminder = false, notification = false,
    _id)))

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  private val ZoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    lazy val controller =
      new KnolxUserAnalysisController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        feedbackFormsResponseRepository,
        feedbackFormsRepository,
        knolxControllerComponent)

    val sessionsRepository: SessionsRepository = mock[SessionsRepository]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]
    val feedbackFormsResponseRepository: FeedbackFormsResponseRepository = mock[FeedbackFormsResponseRepository]
    val categoriesRepository: CategoriesRepository = mock[CategoriesRepository]

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Knolx User Analysis Controller" should {

    "render user Analytics Page" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.renderUserAnalyticsPage()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }


    "send bad request when selected user that doesn't exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test") returns Future(None)

      val result = controller.userSessionsResponseComparison("test")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "send when selected user info for zero sessions" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns Future(Nil)

      val result = controller.userSessionsResponseComparison("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "send when selected user info for some sessions score is zero" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns sessionObject
      feedbackFormsResponseRepository.getScoresOfMembers(_id.stringify, false) returns Future(List(0.0D))

      val result = controller.userSessionsResponseComparison("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "send bad request if user doesn't Exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test") returns Future(None)

      val result = controller.getBanCount("test")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)
      status(result) must be equalTo BAD_REQUEST
    }

    "send ban count if user Exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.getBanCount("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "send bad-request when trying to get user total Knolx Session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test") returns Future(None)

      val result = controller.getUserTotalKnolx("test")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }


    "get user total Knolx Session if there is session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns sessionObject

      val result = controller.getUserTotalKnolx("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)
      status(result) must be equalTo OK
    }

    "get user total Knolx Session if there is no session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns Future(Nil)

      val result = controller.getUserTotalKnolx("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)
      status(result) must be equalTo OK
    }

    "send bad-request when trying to get user total Meetups" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test") returns Future(None)

      val result = controller.getUserTotalMeetUps("test")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "get user total Meetups if there is session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns sessionObject

      val result = controller.getUserTotalMeetUps("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)
      status(result) must be equalTo OK
    }

    "get user total Meetups if there is no session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns Future(Nil)

      val result = controller.getUserTotalMeetUps("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)
      status(result) must be equalTo OK
    }

    "send bad-request when trying to get user total session that he/she didn't attend if user doesn't Exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test") returns Future(None)

      val result = controller.getUserDidNotAttendSessionCount("test")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "get user total total session that he/she didn't attend if there is session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns sessionObject
      feedbackFormsResponseRepository.userCountDidNotAttendSession("test@knoldus.com") returns Future(0)

      val result = controller.getUserDidNotAttendSessionCount("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)
      status(result) must be equalTo OK
    }

    "send when selected user info for some sessions score is not zero" in new WithTestApplication {

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns sessionObject

      feedbackFormsResponseRepository.getScoresOfMembers(_id.stringify, false) returns Future(List(50.0D))

      val result = controller.userSessionsResponseComparison("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK

    }

  }

}

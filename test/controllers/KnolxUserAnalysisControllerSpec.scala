package controllers

import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId}
import java.util.TimeZone

import akka.actor.ActorRef
import akka.util.ByteString
import com.google.inject.name.Names
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.{Application, mvc}
import play.api.libs.streams.Accumulator
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
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, coreMember = false, superUser = true, BSONDateTime(date.getTime), 0, _id)))

  private val sessionObject = Future.successful(List(SessionInfo(_id.stringify, "test@knoldus.com", BSONDateTime(date.getTime), "sessions", "category", "subCategory", "feedbackFormId", "topic",
      1, meetup = true, "rating", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, _id)))

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

    "send user list to template" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.userListSearch(Some("test")) returns Future(List("test@knoldus.com"))

      val result = controller.users(Some("test"))(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "send baqrequest when selected user that doesn't exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test@example.com") returns Future(None)

      val result = controller.userAnalysis("test@example.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "send when selected user info for zero sessions" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns Future(Nil)

      val result = controller.userAnalysis("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "send when selected user info for some sessions" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.userSession("test@knoldus.com") returns sessionObject
      feedbackFormsResponseRepository.getScoresOfMembers(_id.stringify, false) returns Future(List(0.0D))
      feedbackFormsResponseRepository.userCountDidNotAttendSession("test@knoldus.com") returns Future(0)

      val result = controller.userAnalysis("test@knoldus.com")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

  }

}

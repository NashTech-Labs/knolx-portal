package controllers

import java.text.SimpleDateFormat
import java.time.{Instant, ZoneId}
import java.util.TimeZone

import akka.actor.ActorRef
import com.google.inject.name.Names
import helpers.TestEnvironment
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.test.CSRFTokenHelper._
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{FakeRequest, PlaySpecification}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalendarControllerSpec extends PlaySpecification with Mockito {

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("2018-01-02")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject: Future[List[SessionInfo]] =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category",
      "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false, active = true,
      BSONDateTime(date.getTime), Some("youtube/URL/id"), Some("slideShareURL"), temporaryYoutubeURL = None,
      reminder = false, notification = false, _id)))

  private val emailObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))

  private val approveSessionInfo: List[ApproveSessionInfo] = List(ApproveSessionInfo("email",BSONDateTime(date.getTime), "category",
    "subCategory", "topic", meetup = false, approved = false, decline = false, _id))

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    val emailManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

    lazy val controller =
      new CalendarController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        feedbackFormsRepository,
        approveSessionRepository,
        dateTimeUtility,
        config,
        knolxControllerComponent,
        emailManager
      )
    val categoriesRepository = mock[CategoriesRepository]
    val sessionsRepository = mock[SessionsRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val approveSessionRepository = mock[ApprovalSessionsRepository]


    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Calendar Controller" should {

    "render calendar page" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.renderCalendarPage()(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }

    "get list of all Session for Calendar Page" in new WithTestApplication {
      sessionsRepository.getSessionInMonth(1514745000000L, 1517423399999L)  returns sessionObject
      approveSessionRepository.getAllSession returns Future.successful(approveSessionInfo)

      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.calendarSessions(1514745000000L, 1517423399999L)(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }

    "render create session for user for updating" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.toLocalDateTime(1517423399999L) returns Instant.ofEpochMilli(1517423399999L).atZone(ISTZoneId).toLocalDateTime

      val result = controller.renderCreateSessionByUser(None, "1517423399999")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "render create session for user for new session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      approveSessionRepository.getSession(_id.stringify) returns Future.successful(approveSessionInfo.head)

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      dateTimeUtility.toLocalDateTime(date.getTime) returns Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime

      val result = controller.renderCreateSessionByUser(Some(_id.stringify), "1517423399999")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "Receive Bad Request while creating session, Date is empty" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSessionByUser(Some(_id.stringify), "1517423399999")(
      FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withFormUrlEncodedBody("email" -> "test@knoldus.com",
        "category" -> "test category",
        "subCategory" -> "subCategory",
        "topic" -> "topic",
        "meetup" -> "true")
        .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "Receive Bad Request while creating session when form is with error" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423399999L
      dateTimeUtility.toLocalDateTime(1517423399999L) returns Instant.ofEpochMilli(1517423399999L).atZone(ISTZoneId).toLocalDateTime

      val result = controller.createSessionByUser(Some(_id.stringify), "1517423399999")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-01-31T23:59",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "topic" -> "",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "session successfully created by user" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L
      val updateApproveSessionInfo = UpdateApproveSessionInfo("test@knoldus.com",BSONDateTime(1517423340000L), "category",
        "subCategory", "topic", meetup = true, _id.stringify)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      approveSessionRepository.insertSessionForApprove(updateApproveSessionInfo) returns updateWriteResult

      usersRepository.getAllAdminAndSuperUser returns Future.successful(List("test@knoldus.com"))

      val result = controller.createSessionByUser(Some(_id.stringify), "2018-01-31T23:59")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-01-31T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "internal server error while creating session by user" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L
      val updateApproveSessionInfo = UpdateApproveSessionInfo("test@knoldus.com",BSONDateTime(1517423340000L), "category",
        "subCategory", "topic", meetup = true, _id.stringify)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      approveSessionRepository.insertSessionForApprove(updateApproveSessionInfo) returns updateWriteResult

      val result = controller.createSessionByUser(Some(_id.stringify), "2018-01-31T23:59")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-01-31T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "error while creating session by user when dates are not matching" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L

      val result = controller.createSessionByUser(Some(_id.stringify), "2018-01-31T23:59")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-02-31T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "get pending sessions for Notification" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      approveSessionRepository.getAllSession returns Future.successful(approveSessionInfo)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

       val result = controller.getPendingSessions()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "get all sessions for Admin" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      approveSessionRepository.getAllSession returns Future.successful(approveSessionInfo)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.getAllSessionForAdmin()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "update Pending Session Date" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      approveSessionRepository.updateDateForPendingSession(_id.stringify, BSONDateTime(1517423399999L)) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updatePendingSessionDate(_id.stringify, "1517423399999")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "error while updating Pending Session Date" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      approveSessionRepository.updateDateForPendingSession(_id.stringify, BSONDateTime(1517423399999L)) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updatePendingSessionDate(_id.stringify, "1517423399999")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "decline Pending Session " in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      approveSessionRepository.declineSession(_id.stringify) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.declineSession(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "erroe while declining Pending Session " in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      approveSessionRepository.declineSession(_id.stringify) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.declineSession(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

  }

}

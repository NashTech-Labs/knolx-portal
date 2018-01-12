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
import reactivemongo.api.commands.{DefaultWriteResult, UpdateWriteResult}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

class CalendarControllerSpec extends PlaySpecification with Mockito {

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

  private val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2018-01-31T23:59")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject: Future[List[SessionInfo]] =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category",
      "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false, active = true,
      BSONDateTime(date.getTime), Some("youtube/URL/id"), Some("slideShareURL"), temporaryYoutubeURL = None,
      reminder = false, notification = false, _id)))

  private val emailObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))

  private val approveSessionInfo: List[SessionRequestInfo] = List(SessionRequestInfo("email", BSONDateTime(date.getTime), "category",
    "subCategory", "topic", _id = _id))

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    val emailManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

    lazy val controller =
      new CalendarController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        sessionRequestRepository,
        dateTimeUtility,
        config,
        knolxControllerComponent,
        emailManager
      )
    val categoriesRepository = mock[CategoriesRepository]
    val sessionsRepository = mock[SessionsRepository]

    val dateTimeUtility = mock[DateTimeUtility]
    val sessionRequestRepository = mock[SessionRequestRepository]


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

    "get list of all Sessions for Calendar Page when user is not logged in" in new WithTestApplication {
      sessionsRepository.getSessionInMonth(1514745000000L, 1517423399999L) returns sessionObject
      sessionRequestRepository.getSessionsInMonth(1514745000000L, 1517423399999L) returns Future.successful(approveSessionInfo)

      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.calendarSessions(1514745000000L, 1517423399999L)(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }

    "get list of all Sessions for Calendar Page when user is logged in" in new WithTestApplication {
      sessionsRepository.getSessionInMonth(1514745000000L, 1517423399999L) returns sessionObject
      sessionRequestRepository.getSessionsInMonth(1514745000000L, 1517423399999L) returns Future.successful(approveSessionInfo)

      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.calendarSessions(1514745000000L, 1517423399999L)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "render create session for user for creating/updating his session" in new WithTestApplication {
      val sessionRequestInfo = approveSessionInfo.head.copy(freeSlot = true)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(sessionRequestInfo))

      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"

      val result = controller.renderCreateSessionByUser(_id.stringify, isFreeSlot = true)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "render create session for user for creating/updating his session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(None)

      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"

      val result = controller.renderCreateSessionByUser(_id.stringify, isFreeSlot = false)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not render create session for user for creating/updating his session when the free slot doesn't exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"

      val result = controller.renderCreateSessionByUser(_id.stringify, isFreeSlot = true)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "redirect to calendar page while creating session if session/free slot does not exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(None)

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "receive Bad Request while creating session for incorrect form submission" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSessionByUser(_id.stringify)(
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

    "redirect to calendar page while creating session if freeSlotId is empty" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-01-31T23:59",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "create a pending session successfully" in new WithTestApplication {
      val updateApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        topic = "topic",
        email = "test@knoldus.com",
        category = "category",
        subCategory = "subCategory",
        meetup = true)

      val writeResult = DefaultWriteResult(ok = true, 1, Seq(), None, None, None)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      sessionRequestRepository.insertSessionForApprove(updateApproveSessionInfo) returns Future.successful(writeResult)
      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L
      usersRepository.getAllAdminAndSuperUser returns Future.successful(List("test@knoldus.com"))

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-01-31T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "freeSlotId")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not create a pending session due to DB insertion error" in new WithTestApplication {
      val updateApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        topic = "topic",
        email = "test@knoldus.com",
        category = "category",
        subCategory = "subCategory",
        meetup = true)

      val writeResult = DefaultWriteResult(ok = false, 1, Seq(), None, None, None)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      sessionRequestRepository.insertSessionForApprove(updateApproveSessionInfo) returns Future.successful(writeResult)
      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-01-31T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "freeSlotId")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not update date of a pending session if free slot does not exist on specified date" in new WithTestApplication {
      val updateApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        topic = "topic",
        email = "test@knoldus.com",
        category = "category",
        subCategory = "subCategory",
        meetup = true)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      sessionRequestRepository.getSession("freeSlotId") returns Future.successful(None)
      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-02-01T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "freeSlotId")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not update date of a pending session due to DB updation error" in new WithTestApplication {
      val updateApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        topic = "topic",
        email = "test@knoldus.com",
        category = "category",
        subCategory = "subCategory",
        meetup = true)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      sessionRequestRepository.getSession("freeSlotId") returns Future.successful(Some(approveSessionInfo.head))
      sessionRequestRepository.updateDateForPendingSession(_id.stringify, BSONDateTime(1517509740000L)) returns updateWriteResult
      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-02-01T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "freeSlotId")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not update date of free slot due to DB upsertion error" in new WithTestApplication {
      val updateApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        topic = "topic",
        email = "test@knoldus.com",
        category = "category",
        subCategory = "subCategory",
        meetup = true)

      val freeSlot = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        freeSlot = true)

      val writeResult = DefaultWriteResult(ok = false, 1, Seq(), None, None, None)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      sessionRequestRepository.insertSessionForApprove(freeSlot) returns Future.successful(writeResult)
      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      sessionRequestRepository.getSession("freeSlotId") returns Future.successful(Some(approveSessionInfo.head))
      sessionRequestRepository.updateDateForPendingSession(_id.stringify, BSONDateTime(1517509740000L)) returns updateWriteResult
      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-02-01T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "freeSlotId")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "update date of a pending session" in new WithTestApplication {
      val updateApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        topic = "topic",
        email = "test@knoldus.com",
        category = "category",
        subCategory = "subCategory",
        meetup = true)

      val freeSlot = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        freeSlot = true)

      val writeResult = DefaultWriteResult(ok = true, 1, Seq(), None, None, None)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      sessionRequestRepository.insertSessionForApprove(freeSlot) returns Future.successful(writeResult)
      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      sessionRequestRepository.getSession("freeSlotId") returns Future.successful(Some(approveSessionInfo.head))
      sessionRequestRepository.updateDateForPendingSession(_id.stringify, BSONDateTime(1517509740000L)) returns updateWriteResult
      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-02-01T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "freeSlotId")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not update date of a free slot" in new WithTestApplication {
      val updateApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(date.getTime),
        sessionId = _id.stringify,
        topic = "topic",
        email = "test@knoldus.com",
        category = "category",
        subCategory = "subCategory",
        meetup = true)

      val freeSlot = SessionRequestInfo("email", BSONDateTime(date.getTime), "category",
        "subCategory", "topic", _id = _id, freeSlot = true)

      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(freeSlot))
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllFreeSlots returns Future.successful(approveSessionInfo)

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      dateTimeUtility.parseDateStringWithTToIST("2018-01-31T23:59") returns 1517423340999L

      val result = controller.createSessionByUser(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2018-02-01T23:59",
            "category" -> "category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "meetup" -> "true",
            "freeSlotId" -> "freeSlotId")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "get pending sessions for Notification" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.getAllPendingSession returns Future.successful(approveSessionInfo)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.pendingSessions()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "not get all sessions for admin if form submitted is wrong" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.allSessionForAdmin()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test@knoldus.com",
            "pageSize" -> "10")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "get all sessions for admin" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.paginate(1, Some("test@knoldus.com"), 10) returns Future.successful(approveSessionInfo)
      sessionRequestRepository.activeCount(Some("test@knoldus.com")) returns Future.successful(1)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.allSessionForAdmin()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test@knoldus.com",
            "page" -> "1",
            "pageSize" -> "10")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "decline Pending Session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      sessionRequestRepository.declineSession(_id.stringify) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.declineSession(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "error while declining Pending Session " in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      sessionRequestRepository.declineSession(_id.stringify) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.declineSession(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "insert a free slot successfully" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val freeSlot = UpdateApproveSessionInfo(BSONDateTime(1517509740000L), freeSlot = true)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.insertSessionForApprove(freeSlot) returns updateWriteResult

      dateTimeUtility.parseDateStringWithTToIST("2018-02-01T23:59") returns 1517509740000L
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.insertFreeSlot("2018-02-01T23:59")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "not insert a free slot if DB insertion was unsuccessful" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val freeSlot = UpdateApproveSessionInfo(BSONDateTime(1517509740000L), freeSlot = true)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.insertSessionForApprove(freeSlot) returns updateWriteResult

      dateTimeUtility.parseDateStringWithTToIST("2018-02-01T23:59") returns 1517509740000L
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.insertFreeSlot("2018-02-01T23:59")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "delete free sot successfully" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.deleteFreeSlot(_id.stringify) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteFreeSlot(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not delete free slot due to DB deletion error" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionRequestRepository.deleteFreeSlot(_id.stringify) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteFreeSlot(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

  }

}

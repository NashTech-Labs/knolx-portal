package controllers

import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId, LocalTime, Instant}
import java.util.{TimeZone, Date}

import akka.actor.ActorRef
import com.google.inject.name.Names
import com.typesafe.config.ConfigFactory
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.{Configuration, Application}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsBoolean, JsObject, JsString}
import play.api.mvc.Results
import play.api.test._
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility
import play.api.test.CSRFTokenHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SessionsControllerSpec extends PlaySpecification with Results {

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "feedbackFormId", "topic",
      1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  private val ZoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))


  private val emailObject = Future.successful(Some(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, BSONDateTime(date.getTime), 0, _id)))


  private val emptyEmailObject = Future.successful(None)

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()
    lazy val controller =
      new SessionsController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        feedbackFormsRepository,
        dateTimeUtility,
        knolxControllerComponent,
        sessionsScheduler,
        usersBanScheduler)
    val sessionsRepository = mock[SessionsRepository]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val sessionsScheduler =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("SessionsScheduler")))))
    val usersBanScheduler =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("UsersBanScheduler")))))
    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Session Controller" should {

    "display sessions page" in new WithTestApplication {

      sessionsRepository.paginate(1, None) returns sessionObject
      sessionsRepository.activeCount(None) returns Future.successful(1)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.sessions(1, None)(FakeRequest().withCSRFToken)

      contentAsString(result) must be contain "<th>Topic</th>"
      status(result) must be equalTo OK
    }

    "display manage sessions page" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.paginate(1,None) returns sessionObject
      sessionsRepository.activeCount(None) returns Future.successful(1)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.manageSessions(1, None)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "not open manage sessions page when wrong username is specified" in new WithTestApplication {

      usersRepository.getByEmail("") returns emptyEmailObject
      sessionsRepository.sessions returns sessionObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.manageSessions(1, None)(FakeRequest().withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "not open manage sessions page when user is not admin" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject.map(userInfo => userInfo.map(_.copy(admin = false)))
      sessionsRepository.sessions returns sessionObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.manageSessions(1, None)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "not open manage sessions page when unauthorized access is performed" in new WithTestApplication {

      val emailObject = Future.successful(List.empty)

      usersRepository.getByEmail("test@example.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone


      val result = controller.manageSessions(1)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "delete session" in new WithTestApplication {
      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString(_id.stringify), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true), "_id" -> JsString(_id.stringify)))))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.delete(_id.stringify) returns objectToDelete
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteSession(_id.stringify, 1)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not delete session when wrong id is specified" in new WithTestApplication {
      val objectToDelete = Future.successful(None)

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.delete("1") returns objectToDelete
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteSession("1", 1)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not delete session when user is not admin" in new WithTestApplication {
      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString("123"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true)))))

      usersRepository.getByEmail("test@example.com") returns emailObject.map(userInfo => userInfo.map(_.copy(admin = false)))
      sessionsRepository.delete("123") returns objectToDelete
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteSession("123", 1)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(result) must be equalTo UNAUTHORIZED
    }

    "render create session form" in new WithTestApplication {
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.create(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "create session" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)
      val expirationDate = localDateTimeEndOfDay.plusDays(1)
      val expirationMillis = localDateTimeEndOfDay.toEpochSecond(ZoneOffset) * 1000

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.startOfDayMillis returns date.getTime - 16 * 60 * 60 * 1000 + 1000
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSession(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not create session when result is false" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)
      val expirationDate = localDateTimeEndOfDay.plusDays(1)
      val expirationMillis = localDateTimeEndOfDay.toEpochSecond(ZoneOffset) * 1000

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSession(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not create session due to BadFormRequest" in new WithTestApplication {
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      dateTimeUtility.startOfDayMillis returns System.currentTimeMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result =
        controller.createSession(
          FakeRequest(POST, "create")
            .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
            .withFormUrlEncodedBody("email" -> "test@example.com",
              "date" -> "2017-06-21T16:00",
              "feedbackFormId" -> _id.stringify,
              "session" -> "session",
              "topic" -> "topic",
              "meetup" -> "true")
            .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not create session due to Invalid Email" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.getByEmail("test2@example.com") returns Future.successful(None)
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSession(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test2@example.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not create session due to unauthorized access" in new WithTestApplication {
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        controller.createSession(
          FakeRequest(POST, "create")
            .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
            .withFormUrlEncodedBody("email" -> "test@example.com",
              "date" -> sessionDateString,
              "feedbackFormId" -> _id.stringify,
              "session" -> "session 1",
              "topic" -> "topic",
              "meetup" -> "true")
            .withCSRFToken)

      status(result) must be equalTo UNAUTHORIZED
    }

    "render getByEmail session form" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val sessionInfo = Future.successful(Some(SessionInfo(_id.stringify, "test@example.com", BSONDateTime(date.getTime), "session 1",
        "feedbackFormId", "topic", 1, meetup = false, "", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns sessionInfo
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.update(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "redirect to manage sessions page when session is not found" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val sessionInfo = Future.successful(None)

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns sessionInfo
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.update(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not render getByEmail session form/manage session form due to unauthorized access" in new WithTestApplication {

      usersRepository.getByEmail("test@example.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result = controller.update(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(result) must be equalTo UNAUTHORIZED
    }

    "getByEmail session" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)
      val expirationDate = localDateTimeEndOfDay.plusDays(1)
      val expirationMillis = localDateTimeEndOfDay.toEpochSecond(ZoneOffset) * 1000

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val updatedInformation = UpdateSessionInfo(UpdateSessionInformation(_id.stringify, date, "session 1",
        "feedbackFormId", "topic", 1, meetup = true), BSONDateTime(1498415399000L))
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.update(updatedInformation) returns updateWriteResult
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updateSession()(
        FakeRequest(POST, "getByEmail")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("sessionId" -> _id.stringify,
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not getByEmail session when result is false" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)
      val expirationDate = localDateTimeEndOfDay.plusDays(1)
      val expirationMillis = localDateTimeEndOfDay.toEpochSecond(ZoneOffset) * 1000

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val updatedInformation = UpdateSessionInfo(UpdateSessionInformation(_id.stringify, date, "session 1",
        "feedbackFormId", "topic", 1, meetup = true), BSONDateTime(1498415399000L))
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.update(updatedInformation) returns updateWriteResult
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updateSession()(
        FakeRequest(POST, "getByEmail")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("sessionId" -> _id.stringify,
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not getByEmail session due to BadFormRequest" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.startOfDayMillis returns System.currentTimeMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updateSession()(
        FakeRequest(POST, "getByEmail")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("sessionId" -> _id.stringify,
            "date" -> "2017-06-21T16:00",
            "session" -> "session",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not getByEmail session due to unauthorized access" in new WithTestApplication {

      usersRepository.getByEmail("test@example.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        controller.createSession(
          FakeRequest(POST, "create")
            .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
            .withFormUrlEncodedBody("sessionId" -> _id.stringify,
              "date" -> sessionDateString,
              "feedbackFormId" -> _id.stringify,
              "session" -> "session 1",
              "topic" -> "topic",
              "meetup" -> "true")
            .withCSRFToken)

      status(result) must be equalTo UNAUTHORIZED
    }

    "cancel session by session id" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.cancelScheduledSession(_id.stringify)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=").withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }


    "throw a bad request when encountered a invalid value for search session form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.searchSessions()(FakeRequest(POST, "search")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "page" -> "0").withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "schedule session by session id" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.scheduleSession(_id.stringify)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "return json for the session searched by email" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.paginate(1, Some("test@example.com")) returns sessionObject
      sessionsRepository.activeCount(Some("test@example.com")) returns Future.successful(1)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.searchSessions()(FakeRequest(POST, "search")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "page" -> "1"))

      status(result) must be equalTo OK
    }

    "throw a bad request when encountered a invalid value from form for manage Sessions" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      usersRepository.getByEmail("test@example.com") returns emailObject
      val result = controller.searchManageSession()(FakeRequest(POST, "search")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "page" -> "invalid value"))

      status(result) must be equalTo BAD_REQUEST
    }

    "return json for the session to manage when searched by email" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.paginate(1, Some("test@example.com")) returns sessionObject
      sessionsRepository.activeCount(Some("test@example.com")) returns Future.successful(1)

      val result = controller.searchManageSession()(FakeRequest(POST, "search")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "page" -> "1"))

      status(result) must be equalTo OK
    }

  }
}

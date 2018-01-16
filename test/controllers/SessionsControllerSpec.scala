package controllers

import java.text.SimpleDateFormat
import java.time.{Instant, LocalDateTime, LocalTime, ZoneId}
import java.util.{Date, TimeZone}

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Names
import helpers._
import models._
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsBoolean, JsObject, JsString}
import play.api.test.CSRFTokenHelper._
import play.api.test._
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SessionsControllerSpec extends PlaySpecification with Mockito with SpecificationLike with BeforeAllAfterAll {

  private val system = ActorSystem()

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category",
      "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false, active = true,
      BSONDateTime(date.getTime), Some("youtube/URL/id"), Some("slideShareURL"), temporaryYoutubeURL = None,
      reminder = false, notification = false, _id)))

  private val optionOfSessionObject =
    Future.successful(Some(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category",
      "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false, active = true,
      BSONDateTime(date.getTime), Some("youtube/URL/id"), Some("slideShareURL"), temporaryYoutubeURL = None,
      reminder = false, notification = false, _id)))

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  private val ZoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))

  private val emailObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))

  private val emptyEmailObject = Future.successful(None)

  private val approveSessionInfo: List[SessionRequestInfo] = List(SessionRequestInfo("email", BSONDateTime(date.getTime), "category",
    "subCategory", "topic", _id = _id))

  abstract class WithTestApplication extends TestEnvironment with Scope {
    val sessionsRepository = mock[SessionsRepository]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val categoriesRepository = mock[CategoriesRepository]
    val sessionRequestRepository = mock[SessionRequestRepository]
    val recommendationsRepository = mock[RecommendationsRepository]

    val dateTimeUtility = mock[DateTimeUtility]

    lazy val app = fakeApp()

    val emailManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))
    val sessionsScheduler =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("SessionsScheduler")))))
    val usersBanScheduler =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("UsersBanScheduler")))))
    val youtubeManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeManager")))))

    lazy val controller =
      new SessionsController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        feedbackFormsRepository,
        sessionRequestRepository,
        recommendationsRepository,
        dateTimeUtility,
        config,
        knolxControllerComponent,
        emailManager,
        sessionsScheduler,
        usersBanScheduler,
        youtubeManager)
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "Session Controller" should {

    "display sessions page" in new WithTestApplication {

      sessionsRepository.paginate(1, None) returns sessionObject
      sessionsRepository.activeCount(None) returns Future.successful(1)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.sessions()(FakeRequest().withCSRFToken)

      contentAsString(result) must be contain "<th>Topic</th>"
      status(result) must be equalTo OK
    }

    "display manage sessions page" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.paginate(1, None) returns sessionObject
      sessionsRepository.activeCount(None) returns Future.successful(1)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.manageSessions()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "not open manage sessions page when wrong username is specified" in new WithTestApplication {

      usersRepository.getByEmail("") returns emptyEmailObject
      sessionsRepository.sessions returns sessionObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.manageSessions()(FakeRequest().withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo SEE_OTHER
    }

    "not open manage sessions page when user is not admin" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject.map(userInfo => userInfo.map(_.copy(admin = false)))
      sessionsRepository.sessions returns sessionObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.manageSessions()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo SEE_OTHER
    }

    "not open manage sessions page when unauthorized access is performed" in new WithTestApplication {

      val emailObject = Future.successful(List.empty)

      usersRepository.getByEmail("test@knoldus.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone


      val result = controller.manageSessions()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo SEE_OTHER
    }

    "delete session" in new WithTestApplication {
      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString(_id.stringify), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true), "_id" -> JsString(_id.stringify)))))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.delete(_id.stringify) returns objectToDelete
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteSession(_id.stringify, 1)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not delete session when wrong id is specified" in new WithTestApplication {
      val objectToDelete = Future.successful(None)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.delete("1") returns objectToDelete
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteSession("1", 1)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not delete session when user is not admin" in new WithTestApplication {
      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString("123"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true)))))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject.map(userInfo => userInfo.map(_.copy(admin = false)))
      sessionsRepository.delete("123") returns objectToDelete
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.deleteSession("123", 1)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "render create session form" in new WithTestApplication {
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.create(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
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
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.startOfDayMillis returns date.getTime - 16 * 60 * 60 * 1000 + 1000
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSession(
        FakeRequest(POST, "create")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
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
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSession(
        FakeRequest(POST, "create")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
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
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.startOfDayMillis returns System.currentTimeMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result =
        controller.createSession(
          FakeRequest(POST, "create")
            .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
            .withFormUrlEncodedBody("email" -> "test@knoldus.com",
              "date" -> "2017-06-21T16:00",
              "feedbackFormId" -> _id.stringify,
              "session" -> "session",
              "category" -> "test category",
              "subCategory" -> "subCategory",
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
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test2@example.com") returns Future.successful(None)
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.createSession(
        FakeRequest(POST, "create")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("email" -> "test2@example.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not create session due to unauthorized access" in new WithTestApplication {
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?",
        List("1", "2", "3"), "MCQ", mandatory = true))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@knoldus.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        controller.createSession(
          FakeRequest(POST, "create")
            .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
            .withFormUrlEncodedBody("email" -> "test@knoldus.com",
              "date" -> sessionDateString,
              "feedbackFormId" -> _id.stringify,
              "session" -> "session 1",
              "category" -> "test category",
              "subCategory" -> "subCategory",
              "topic" -> "topic",
              "meetup" -> "true")
            .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "render getByEmail session form" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))
      val sessionInfo = Future.successful(Some(SessionInfo(_id.stringify, "test@knoldus.com", BSONDateTime(date.getTime), "session 1", "category",
        "subCategory", "feedbackFormId", "topic", 1, meetup = false, "", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtube/URL/id"), Some("slideShareURL"), temporaryYoutubeURL = None, reminder = false, notification = false, _id)))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns sessionInfo
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.update(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "render update session page when youtube url is empty" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)

      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))
      val sessionInfo = Future.successful(Some(SessionInfo(_id.stringify, "test@knoldus.com",
        BSONDateTime(date.getTime), "session 1", "category", "subCategory", "feedbackFormId", "topic", 1,
        meetup = false, "", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), None,
        Some("slideShareURL"), temporaryYoutubeURL = None, reminder = false, notification = false, _id)))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns sessionInfo
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.update(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "redirect to manage sessions page when session is not found" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val sessionInfo = Future.successful(None)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns sessionInfo
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.update(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not render getByEmail session form/manage session form due to unauthorized access" in new WithTestApplication {

      usersRepository.getByEmail("test@knoldus.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result = controller.update(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "getByEmail session" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)
      val expirationDate = localDateTimeEndOfDay.plusDays(1)
      val expirationMillis = localDateTimeEndOfDay.toEpochSecond(ZoneOffset) * 1000

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val updatedInformation = UpdateSessionInfo(UpdateSessionInformation(_id.stringify, date, "session 1", "test category", "subCategory",
        "feedbackFormId", "topic", 1, Some("youtubeURL"), Some("slideShareURL"), cancelled = false, meetup = true), BSONDateTime(1498415399000L))
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.update(updatedInformation) returns updateWriteResult
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updateSession()(
        FakeRequest(POST, "getByEmail")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("sessionId" -> _id.stringify,
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "youtubeURL" -> "youtubeURL",
            "slideShareURL" -> "slideShareURL",
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

      val updatedInformation = UpdateSessionInfo(UpdateSessionInformation(_id.stringify, date, "session 1", "test category", "subCategory",
        "feedbackFormId", "topic", 1, Some("youtubeURL"), Some("slideShareURL"), cancelled = false, meetup = true), BSONDateTime(1498415399000L))
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.update(updatedInformation) returns updateWriteResult
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.toMillis(expirationDate) returns expirationMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updateSession()(
        FakeRequest(POST, "getByEmail")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("sessionId" -> _id.stringify,
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "youtubeURL" -> "youtubeURL",
            "slideShareURL" -> "slideShareURL",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not getByEmail session due to BadFormRequest" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.startOfDayMillis returns System.currentTimeMillis
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.updateSession()(
        FakeRequest(POST, "getByEmail")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody("sessionId" -> _id.stringify,
            "date" -> "2017-06-21T16:00",
            "session" -> "session",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "meetup" -> "true")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not getByEmail session due to unauthorized access" in new WithTestApplication {

      usersRepository.getByEmail("test@knoldus.com") returns emptyEmailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        controller.createSession(
          FakeRequest(POST, "create")
            .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
            .withFormUrlEncodedBody("sessionId" -> _id.stringify,
              "date" -> sessionDateString,
              "feedbackFormId" -> _id.stringify,
              "session" -> "session 1",
              "category" -> "test category",
              "subCategory" -> "subCategory",
              "topic" -> "topic",
              "meetup" -> "true")
            .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "cancel session by session id" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsScheduler ! InsertTrue

      val result = controller.cancelScheduledSession(_id.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=").withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "do not cancel session by wrong session id" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsScheduler ! InsertFalse

      val result = controller.cancelScheduledSession(_id.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=").withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "throw a bad request when encountered a invalid value for search session form" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.searchSessions()(FakeRequest(POST, "search")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withFormUrlEncodedBody(
          "email" -> "test@knoldus.com",
          "page" -> "0").withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "schedule session by session id" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.scheduleSession(_id.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "return json for the session searched by email" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.paginate(1, Some("test@knoldus.com"), 10) returns sessionObject
      sessionsRepository.activeCount(Some("test@knoldus.com")) returns Future.successful(1)
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.searchSessions()(FakeRequest(POST, "search")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withFormUrlEncodedBody(
          "email" -> "test@knoldus.com",
          "page" -> "1",
          "pageSize" -> "10"))

      status(result) must be equalTo OK
    }

    "throw a bad request when encountered a invalid value from form for manage Sessions" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val result = controller.searchManageSession()(FakeRequest(POST, "search")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withFormUrlEncodedBody(
          "email" -> "test@knoldus.com",
          "page" -> "invalid value",
          "pageSize" -> "10"))

      status(result) must be equalTo BAD_REQUEST
    }

    "return json for the session to manage when searched by email for empty slideshare url" in new WithTestApplication {
      val sessionInfo = Future.successful(List(SessionInfo(_id.stringify, "test@knoldus.com",
        BSONDateTime(date.getTime), "session 1", "category", "subCategory", "feedbackFormId", "topic", 1,
        meetup = false, "", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtube/URL/id"),
        None, temporaryYoutubeURL = None, reminder = false, notification = false, _id)))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.paginate(1, Some("test@knoldus.com"), 10) returns sessionInfo
      sessionsRepository.activeCount(Some("test@knoldus.com")) returns Future.successful(1)

      val result = controller.searchManageSession()(FakeRequest(POST, "search")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withFormUrlEncodedBody(
          "email" -> "test@knoldus.com",
          "page" -> "1",
          "pageSize" -> "10"))

      status(result) must be equalTo OK
    }

    "return json for session to manage when searched by email for empty youtube url" in new WithTestApplication {
      val sessionInfo = Future.successful(List(SessionInfo(_id.stringify, "test@knoldus.com",
        BSONDateTime(date.getTime), "session 1", "category", "subCategory", "feedbackFormId", "topic", 1,
        meetup = false, "", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), None,
        Some("slideShareURL"), temporaryYoutubeURL = None, reminder = false, notification = false, _id)))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.paginate(1, Some("test@knoldus.com"), 10) returns sessionInfo
      sessionsRepository.activeCount(Some("test@knoldus.com")) returns Future.successful(1)

      val result = controller.searchManageSession()(FakeRequest(POST, "search")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withFormUrlEncodedBody(
          "email" -> "test@knoldus.com",
          "page" -> "1",
          "pageSize" -> "10"))

      status(result) must be equalTo OK
    }

    "render page with links to share content on social media" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns optionOfSessionObject

      val result = controller.shareContent(_id.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withCSRFToken)

      status(result) must be equalTo OK
    }

    "render home page when session not found" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns Future.successful(None)

      val result = controller.shareContent(_id.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "render home page when session's id is wrong" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.shareContent("abcdef")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "sent email to the presenter once added to the portal" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns optionOfSessionObject

      val localDateTime = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime
      dateTimeUtility.toLocalDateTime(date.getTime) returns localDateTime
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      val result = controller.sendEmailToPresenter(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "do not sent email to the presenter when wrong id is specified" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns Future(None)

      val localDateTime = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime
      dateTimeUtility.toLocalDateTime(date.getTime) returns localDateTime
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.sendEmailToPresenter(_id.stringify)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "render approve session page for admin" in new WithTestApplication {
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.renderScheduleSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "not render approve session page for admin when session does not exist" in new WithTestApplication {
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(None)

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.renderScheduleSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not approve a session if the session does not exist" in new WithTestApplication {
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(None)

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.approveSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true"
          )
          .withCSRFToken
      )

      status(result) must be equalTo SEE_OTHER
    }

    "not approve a session if submitted form has some errors" in new WithTestApplication {
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.approveSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true"
          )
          .withCSRFToken
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "not approve a session if email is invalid or does not exist" in new WithTestApplication {
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test123@knoldus.com") returns Future.successful(None)
      feedbackFormsRepository.getAll returns getAll
      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.approveSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test123@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true"
          )
          .withCSRFToken
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "not approve a session due to DB insertion error while inserting in sessions repository" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test123@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll

      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.approveSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test123@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true"
          )
          .withCSRFToken
      )

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not approve a session due to DB insertion error while inserting in approval sessions repository" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val wrongUpdateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test123@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll

      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      sessionRequestRepository.insertSessionForApprove(any[UpdateApproveSessionInfo])(any[ExecutionContext])
        .returns(wrongUpdateWriteResult)

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.approveSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test123@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true"
          )
          .withCSRFToken
      )

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "approve a session and insert it in session repository" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val localDateTimeEndOfDay = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test123@knoldus.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll

      sessionRequestRepository.getSession(_id.stringify) returns Future.successful(Some(approveSessionInfo.head))
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult
      sessionRequestRepository.insertSessionForApprove(any[UpdateApproveSessionInfo])(any[ExecutionContext])
        .returns(updateWriteResult)

      dateTimeUtility.formatDateWithT(date) returns "formattedDate"
      dateTimeUtility.toLocalDateTimeEndOfDay(date) returns localDateTimeEndOfDay
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.approveSessionByAdmin(_id.stringify, None)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withFormUrlEncodedBody(
            "email" -> "test123@knoldus.com",
            "date" -> "2017-06-25T16:00",
            "session" -> "session 1",
            "category" -> "test category",
            "subCategory" -> "subCategory",
            "feedbackFormId" -> "feedbackFormId",
            "topic" -> "topic",
            "feedbackExpirationDays" -> "1",
            "meetup" -> "true"
          )
          .withCSRFToken
      )

      status(result) must be equalTo SEE_OTHER
    }

  }

}

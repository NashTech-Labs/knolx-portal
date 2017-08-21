package controllers

import java.text.SimpleDateFormat

import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.mvc.Results
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, _}
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FeedbackFormsResponseControllerSpec extends PlaySpecification with Results {

  val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))
  val writeResultfalse = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "feedbackFormId", "topic",
      1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))
  private val noActiveSessionObject = Future.successful(Nil)
  private val emailObject = Future.successful(Some(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, BSONDateTime(date.getTime), 0, _id)))
  private val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true),
    Question("How is the UI?", List("1"), "COMMENT", mandatory = true)),
    active = true, _id)
  private val questionResponseInformation = QuestionResponse("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "2")
  private val feedbackResponse = FeedbackFormsResponse("test@example.com", "presenter@example.com", _id.stringify, _id.stringify,
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
      new FeedbackFormsResponseController(
        knolxControllerComponent.messagesApi,
        mailerClient,
        usersRepository,
        feedbackFormsRepository,
        feedbackResponseRepository,
        sessionsRepository,
        dateTimeUtility,
        knolxControllerComponent)

    val mailerClient = mock[MailerClient]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]
    val feedbackResponseRepository: FeedbackFormsResponseRepository = mock[FeedbackFormsResponseRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val sessionsRepository = mock[SessionsRepository]

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Feedback Response Controller" should {

    "not render feedback form for today if session associated feedback form not found" in new WithTestApplication {
      usersRepository.getActiveAndUnbanned("test@example.com") returns emailObject
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(None)

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form exists and session has not expired" in new WithTestApplication {
      usersRepository.getActiveAndUnbanned("test@example.com") returns emailObject
      val sessionObjectWithCurrentDate =
        Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(System.currentTimeMillis), "sessions", "feedbackFormId", "topic",
          1, meetup = true, "rating", cancelled = false, active = true, BSONDateTime(date.getTime), _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObjectWithCurrentDate
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form exists and session has expired expired" in new WithTestApplication {
      usersRepository.getActiveAndUnbanned("test@example.com") returns emailObject
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today with immidiate explored sessions if no active sessions found" in new WithTestApplication {
      usersRepository.getActiveAndUnbanned("test@example.com") returns emailObject
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.activeSessions() returns noActiveSessionObject
      sessionsRepository.immediatePreviousExpiredSessions returns sessionObject

      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=").withCSRFToken)

      status(response) must be equalTo OK
    }

    "not render feedback form for today if user is blocked" in new WithTestApplication {
      usersRepository.getActiveAndUnbanned("test@example.com") returns Future.successful(None)
      usersRepository.getByEmail("test@example.com") returns emailObject

      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=").withCSRFToken)

      status(response) must be equalTo UNAUTHORIZED
    }

    "not fetch response as no stored response found" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackResponseRepository.getByUsersSession(_id.stringify, _id.stringify) returns Future.successful(None)

      val response = controller.fetchFeedbackFormResponse(_id.stringify)(FakeRequest(GET, "fetch")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo NOT_FOUND

    }

    "fetch response" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackResponseRepository.getByUsersSession(_id.stringify, _id.stringify) returns Future.successful(Some(feedbackResponse))

      val response = controller.fetchFeedbackFormResponse(_id.stringify)(FakeRequest(GET, "fetch")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(response) must be equalTo OK

    }

    "throw a bad request as submit response form with invalid field submitted" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse("""{"feedbackFormId":"", "responses":[]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no feedback form id submitted" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"", "responses":["a"]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no session id submitted" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"", "feedbackFormId":"${_id.stringify}", "responses":["a"]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no feedback form response submitted" in new WithTestApplication {

      usersRepository.getByEmail("test@example.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":[]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no active session available with the session id submitted by form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns Future.successful(None)

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a"]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is active session available but no feedback form available with feedback form  " +
      "id submitted by form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(None)

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a"]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is more responses then the questions available in the feedback form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a","b"]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "store feedback form response" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResult
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"]}""")))

      status(response) must be equalTo OK
    }

    "not store feedback form response due to internal server error" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResultfalse
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"]}""")))

      status(response) must be equalTo INTERNAL_SERVER_ERROR
    }

    "throw a bad request if there is responses which are not present as multiple choices in feedback form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a"]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is responses of mandatory comment which is empty" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2",""]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there a mcq question and its not mandatory" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = false),
        Question("How is the UI?", List("1"), "COMMENT", mandatory = true)),
        active = true, _id)

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"]}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "store feedback form response for comment type is false" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true),
        Question("How is the UI?", List("1"), "COMMENT", mandatory = false)),
        active = true, _id)

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResult
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2",""]}""")))

      status(response) must be equalTo OK
    }

    "throw a bad request if question type or mandatory type is invalid" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = false),
        Question("How is the UI?", List("1"), "Some other type", mandatory = false)),
        active = true, _id)

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(x => Some(x.head))
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2",""]}""")))

      status(response) must be equalTo BAD_REQUEST
    }
  }

}

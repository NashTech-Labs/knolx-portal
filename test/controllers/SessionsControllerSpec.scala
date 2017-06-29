package controllers

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.ActorRef
import com.google.inject.name.Names
import com.typesafe.config.ConfigFactory
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.ShouldThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsBoolean, JsObject, JsString}
import play.api.test._
import play.api.{Application, Configuration, Environment}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SessionsControllerSpec extends PlaySpecification with TestEnvironment {

  abstract class WithTestApplication(val app: Application = fakeApp) extends Around
    with Scope with ShouldThrownExpectations with Mockito {

    val feedbackFormsScheduler =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("FeedbackFormsScheduler")))))

    val sessionsRepository = mock[SessionsRepository]
    val usersRepository = mock[UsersRepository]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val config = Configuration(ConfigFactory.load("application.conf"))

    val messages = new DefaultMessagesApi(Environment.simple(), config, new DefaultLangs(config))

    val controller =
      new SessionsController(
        messages,
        usersRepository,
        sessionsRepository,
        feedbackFormsRepository,
        dateTimeUtility,
        feedbackFormsScheduler)

    override def around[T: AsResult](t: => T): Result = Helpers.running(app)(AsResult.effectively(t))
  }

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "feedbackFormId", "topic",
      meetup = true, "rating", cancelled = false, active = true, _id)))

  "Session Controller" should {

    "display sessions page" in new WithTestApplication {
      sessionsRepository.paginate(1) returns sessionObject
      sessionsRepository.activeCount returns Future.successful(1)

      val result = controller.sessions(1)(FakeRequest())

      contentAsString(result) must be contain "<th>Topic</th>"
      status(result) must be equalTo OK
    }

    "display manage sessions page" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.paginate(1) returns sessionObject
      sessionsRepository.activeCount returns Future.successful(1)

      val result = controller.manageSessions(1)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "not open manage sessions page when wrong username is specified" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)

      usersRepository.getByEmail("") returns emailObject
      sessionsRepository.sessions returns sessionObject

      val result = controller.manageSessions(1)(FakeRequest())

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "not open manage sessions page when user is not admin" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.sessions returns sessionObject

      val result = controller.manageSessions(1)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "not open manage sessions page when unauthorized access is performed" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)
      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.manageSessions(1)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "delete session" in new WithTestApplication {
      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString(_id.stringify), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true), "_id" -> JsString(_id.stringify)))))

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.delete(_id.stringify) returns objectToDelete

      val result = controller.deleteSession(_id.stringify, 1)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo SEE_OTHER
    }

    "not delete session when wrong id is specified" in new WithTestApplication {
      val objectToDelete = Future.successful(None)

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.delete("1") returns objectToDelete

      val result = controller.deleteSession("1", 1)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not delete session when user is not admin" in new WithTestApplication {
      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString("123"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true)))))

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.delete("123") returns objectToDelete

      val result = controller.deleteSession("123", 1)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo UNAUTHORIZED
    }

    "render create session form" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3")))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.create(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo OK
    }

    "create session" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3")))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.createSession(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "date" -> "2017-06-25T16:00",
          "session" -> "session 1",
          "feedbackFormId" -> "feedbackFormId",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo SEE_OTHER
    }

    "not create session when result is false" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3")))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.createSession(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "date" -> "2017-06-25T16:00",
          "session" -> "session 1",
          "feedbackFormId" -> "feedbackFormId",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not create session due to BadFormRequest" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3")))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      dateTimeUtility.startOfDayMillis returns System.currentTimeMillis()

      val result =
        controller.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> "2017-06-21T16:00",
            "feedbackFormId" -> _id.stringify,
            "session" -> "session",
            "topic" -> "topic",
            "meetup" -> "true"))

      status(result) must be equalTo BAD_REQUEST
    }

    "not create session due to Invalid Email" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3")))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.getByEmail("test2@example.com") returns Future.successful(Nil)
      sessionsRepository.insert(any[SessionInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.createSession(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test2@example.com",
          "date" -> "2017-06-25T16:00",
          "session" -> "session 1",
          "feedbackFormId" -> "feedbackFormId",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo BAD_REQUEST
    }

    "not create session due to unauthorized access" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)
      val feedbackForms = List(FeedbackForm("Test Form", List(Question("How good is knolx portal ?", List("1", "2", "3")))))

      feedbackFormsRepository.getAll returns Future(feedbackForms)
      usersRepository.getByEmail("test@example.com") returns emailObject

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        controller.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> sessionDateString,
            "feedbackFormId" -> _id.stringify,
            "session" -> "session 1",
            "topic" -> "topic",
            "meetup" -> "true"))

      status(result) must be equalTo UNAUTHORIZED
    }

    "render update session form" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"))
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      val sessionInfo = Future.successful(Some(SessionInfo(_id.stringify, "test@example.com", BSONDateTime(date.getTime), "session 1",
        "feedbackFormId", "topic", meetup = false, "", cancelled = false, active = true, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns sessionInfo
      feedbackFormsRepository.getAll returns getAll

      val result = controller.update(_id.stringify)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo OK
    }

    "redirect to manage sessions page when session is not found" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      val sessionInfo = Future.successful(None)

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.getById(_id.stringify) returns sessionInfo

      val result = controller.update(_id.stringify)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo SEE_OTHER
    }

    "not render update session form/manage session form due to unauthorized access" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)

      usersRepository.getByEmail("test@example.com") returns emailObject

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result = controller.update(_id.stringify)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo UNAUTHORIZED
    }

    "update session" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"))
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))

      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))
      val updatedInformation = UpdateSessionInformation(_id.stringify, date, "session 1", "feedbackFormId", "topic", meetup = true)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.update(updatedInformation) returns updateWriteResult
      feedbackFormsRepository.getAll returns getAll

      val result = controller.updateSession()(FakeRequest(POST, "update")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("sessionId" -> _id.stringify,
          "date" -> "2017-06-25T16:00",
          "session" -> "session 1",
          "feedbackFormId" -> "feedbackFormId",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo SEE_OTHER
    }

    "not update session when result is false" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"))
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      val updatedInformation = UpdateSessionInformation(_id.stringify, date, "session 1", "feedbackFormId", "topic", meetup = true)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@example.com") returns emailObject
      sessionsRepository.update(updatedInformation) returns updateWriteResult
      feedbackFormsRepository.getAll returns getAll

      val result = controller.updateSession()(FakeRequest(POST, "update")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("sessionId" -> _id.stringify,
          "date" -> "2017-06-25T16:00",
          "session" -> "session 1",
          "feedbackFormId" -> "feedbackFormId",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not update session due to BadFormRequest" in new WithTestApplication {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val questions = Question("How good is knolx portal?", List("1", "2", "3", "4", "5"))
      val getAll = Future.successful(List(FeedbackForm("Test Form", List(questions))))
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject
      feedbackFormsRepository.getAll returns getAll
      dateTimeUtility.startOfDayMillis returns System.currentTimeMillis()

      val result = controller.updateSession()(FakeRequest(POST, "update")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("sessionId" -> _id.stringify,
          "date" -> "2017-06-21T16:00",
          "session" -> "session",
          "feedbackFormId" -> "feedbackFormId",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo BAD_REQUEST
    }

    "not update session due to unauthorized access" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)

      usersRepository.getByEmail("test@example.com") returns emailObject

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        controller.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("sessionId" -> _id.stringify,
            "date" -> sessionDateString,
            "feedbackFormId" -> _id.stringify,
            "session" -> "session 1",
            "topic" -> "topic",
            "meetup" -> "true"))

      status(result) must be equalTo UNAUTHORIZED
    }
  }

}

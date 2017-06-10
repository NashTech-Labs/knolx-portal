package controllers

import java.text.SimpleDateFormat
import java.util.Date

import com.typesafe.config.ConfigFactory
import models.{SessionsRepository, UsersRepository}
import org.specs2.mock.Mockito
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, MessagesApi}
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString}
import play.api.test.{FakeRequest, PlaySpecification, WithApplication}
import play.api.{Configuration, Environment}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONDocument}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionsControllerSpec extends PlaySpecification with Mockito {

  val sessionObject: Future[List[JsObject]] = Future.successful(List(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
    "UserId" -> JsString("user_id"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
    "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
    "Rating" -> JsString("rating"), "Active" -> JsString("Active")))))

  "Session Controller" should {

    "display sessions page" in {
      val sessionController = testObject

      sessionController.sessionsRepository.sessions returns sessionObject

      val result = sessionController.sessionController.sessions(FakeRequest())

      contentAsString(result) must be contain "<th>Topic</th>"
      status(result) must be equalTo OK
    }

    "display manage sessions page" in new WithApplication {
      val sessionController = testObject

      val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.sessions returns sessionObject

      val result = sessionController.sessionController.manageSessions(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      contentAsString(result) must be contain "<h3 class=\"panel-title\">Sessions</h3>"
      status(result) must be equalTo OK
    }

    "not open manage sessions page when wrong username is specified" in new WithApplication {
      val sessionController = testObject

      val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))

      sessionController.usersRepository.getByEmail("") returns Future.successful(List())
      sessionController.sessionsRepository.sessions returns sessionObject

      val result = sessionController.sessionController.manageSessions(FakeRequest())

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "not open manage sessions page when user is not admin" in new WithApplication {
      val sessionController = testObject

      val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.sessions returns sessionObject

      val result = sessionController.sessionController.manageSessions(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "not open manage sessions page when unauthorized access is performed" in new WithApplication {
      val sessionController = testObject

      val emailObject = Future.successful(List.empty)
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject

      val result = sessionController.sessionController.manageSessions(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "delete session" in new WithApplication {
      val sessionController = testObject

      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString("123"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true)))))
      val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.delete("123") returns objectToDelete

      val result = sessionController.sessionController.deleteSession("123")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo OK
    }

    "not delete session when wrong id is specified" in new WithApplication {
      val sessionController = testObject

      val objectToDelete = Future.successful(None)
      val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.delete("1") returns objectToDelete

      val result = sessionController.sessionController.deleteSession("1")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not delete session when user is not admin" in new WithApplication {
      val sessionController = testObject

      val objectToDelete =
        Future.successful(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
          "UserId" -> JsString("123"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
          "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
          "Rating" -> JsString("rating"), "Active" -> JsBoolean(true)))))
      val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.delete("123") returns objectToDelete

      val result = sessionController.sessionController.deleteSession("123")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo UNAUTHORIZED
    }

    "render create session form" in new WithApplication {
      val sessionController = testObject
      val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject

      val result = sessionController.sessionController.create(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo OK
    }


    "create session" in new WithApplication {
      val sessionController = testObject

      val document = BSONDocument(
        "user_id" -> BSONDocument("$oid" -> "593938f42e00002e008516ee"),
        "email" -> "test@example.com",
        "date" -> BSONDateTime(1497119400000L),
        "session" -> "session 1",
        "topic" -> "topic",
        "meetup" -> true,
        "rating" -> "",
        "cancelled" -> false,
        "active" -> true)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val jsObj = JsObject(Map("_id" -> JsObject(Map("$oid" -> JsString("593938f42e00002e008516ee"))),
        "user_id" -> JsString("userId"),
        "email" -> JsString("test@example.com"),
        "date" -> JsObject(Map("$date" -> JsNumber(1497119400000L))),
        "session" -> JsString("session 1"),
        "topic" -> JsString("topic"),
        "meetup" -> JsBoolean(true),
        "rating" -> JsString(""),
        "cancelled" -> JsBoolean(false),
        "active" -> JsBoolean(true)
      ))
      val emailObject = Future.successful(List(jsObj))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.create(document) returns updateWriteResult

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        sessionController.sessionController.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> sessionDateString,
            "session" -> "session 1",
            "topic" -> "topic",
            "meetup" -> "true"))

      status(result) must be equalTo SEE_OTHER
    }

    "not create session when result is false" in new WithApplication {
      val sessionController = testObject

      val document = BSONDocument(
        "user_id" -> BSONDocument("$oid" -> "593938f42e00002e008516ee"),
        "email" -> "test@example.com",
        "date" -> BSONDateTime(1497119400000L),
        "session" -> "session 1",
        "topic" -> "topic",
        "meetup" -> true,
        "rating" -> "",
        "cancelled" -> false,
        "active" -> true)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val jsObj = JsObject(Map("_id" -> JsObject(Map("$oid" -> JsString("593938f42e00002e008516ee"))),
        "user_id" -> JsString("userId"),
        "email" -> JsString("test@example.com"),
        "date" -> JsObject(Map("$date" -> JsNumber(1497119400000L))),
        "session" -> JsString("session"),
        "topic" -> JsString("topic"),
        "meetup" -> JsBoolean(true),
        "rating" -> JsString(""),
        "cancelled" -> JsBoolean(false),
        "active" -> JsBoolean(true)
      ))
      val emailObject = Future.successful(List(jsObj))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.create(document) returns updateWriteResult

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        sessionController.sessionController.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> sessionDateString,
            "session" -> "session 1",
            "topic" -> "topic",
            "meetup" -> "true"))

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not create session due to BadFormRequest" in new WithApplication {
      val sessionController = testObject

      val jsObj = JsObject(Map("_id" -> JsObject(Map("$oid" -> JsString("593938f42e00002e008516ee"))),
        "user_id" -> JsString("userId"),
        "email" -> JsString("test@example.com"),
        "date" -> JsObject(Map("$date" -> JsNumber(1497119400000L))),
        "session" -> JsString("session"),
        "topic" -> JsString("topic"),
        "meetup" -> JsBoolean(true),
        "rating" -> JsString(""),
        "cancelled" -> JsBoolean(false),
        "active" -> JsBoolean(true)
      ))
      val emailObject = Future.successful(List(jsObj))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        sessionController.sessionController.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> sessionDateString,
            "session" -> "session",
            "topic" -> "topic",
            "meetup" -> "true"))

      status(result) must be equalTo BAD_REQUEST

    }


    "not create session due to unauthorized access" in new WithApplication {
      val sessionController = testObject

      val emailObject = Future.successful(List.empty)

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        sessionController.sessionController.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> sessionDateString,
            "session" -> "session 1",
            "topic" -> "topic",
            "meetup" -> "true"))

      status(result) must be equalTo UNAUTHORIZED

    }
  }

  def testObject: TestObject = {
    val mockedSessionsRepository: SessionsRepository = mock[SessionsRepository]
    val mockedUsersRepository: UsersRepository = mock[UsersRepository]

    val config = Configuration(ConfigFactory.load("application.conf"))
    val messages = new DefaultMessagesApi(Environment.simple(), config, new DefaultLangs(config))

    val controller = new SessionsController(messages, mockedUsersRepository, mockedSessionsRepository)

    TestObject(messages, mockedUsersRepository, mockedSessionsRepository, controller)
  }

  case class TestObject(messagesApi: MessagesApi,
                        usersRepository: UsersRepository,
                        sessionsRepository: SessionsRepository,
                        sessionController: SessionsController)

}

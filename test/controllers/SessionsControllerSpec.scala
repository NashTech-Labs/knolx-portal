package controllers

import java.text.SimpleDateFormat
import java.util.Date

import com.typesafe.config.ConfigFactory
import models.{SessionInfo, SessionsRepository, UserInfo, UsersRepository}
import org.specs2.mock.Mockito
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, MessagesApi}
import play.api.libs.json.{JsBoolean, JsObject, JsString}
import play.api.test.{FakeRequest, PlaySpecification, WithApplication}
import play.api.{Configuration, Environment}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionsControllerSpec extends PlaySpecification with Mockito {

  val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  val sessionObject = Future.successful(List(SessionInfo("userId", "email", date, "sessions", "topic", true, "rating", false, true)))

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

     // val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))
      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.sessions returns sessionObject

      val result = sessionController.sessionController.manageSessions(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      contentAsString(result) must be contain "<h3 class=\"panel-title\">Sessions</h3>"
      status(result) must be equalTo OK
    }

    "not open manage sessions page when wrong username is specified" in new WithApplication {
      val sessionController = testObject

      val emailObject = Future.successful(List.empty)

      sessionController.usersRepository.getByEmail("") returns emailObject
      sessionController.sessionsRepository.sessions returns sessionObject

      val result = sessionController.sessionController.manageSessions(FakeRequest())

      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "not open manage sessions page when user is not admin" in new WithApplication {
      val sessionController = testObject

      //val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))

      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))
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
     // val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))
      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.delete("123") returns objectToDelete

      val result = sessionController.sessionController.deleteSession(_id.stringify)(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo OK
    }

    "not delete session when wrong id is specified" in new WithApplication {
      val sessionController = testObject

      val objectToDelete = Future.successful(None)
      //val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))

      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))
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
      //val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))

      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.delete("123") returns objectToDelete

      val result = sessionController.sessionController.deleteSession("123")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo UNAUTHORIZED
    }

    "render create session form" in new WithApplication {
      val sessionController = testObject
      //val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))

      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject

      val result = sessionController.sessionController.create(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo OK
    }

    "not create session when email already exists" in new WithApplication {
      val sessionController = testObject
      val _id: BSONObjectID = BSONObjectID.generate
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")
      val document = SessionInfo("123", "test@example.com", date, "session 1", "topic", true, "", false, true, _id)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      /*val jsObj = JsObject(Map("_id" -> JsObject(Map("$oid" -> JsString("593938f42e00002e008516ee"))),
        "email" -> JsString("test@example.com"),
        "password" -> JsString("abc"),
        "algorithm" -> JsString("BCrypt"),
        "active" -> JsBoolean(true),
        "admin" -> JsBoolean(false)
      ))*/
      //val emailObject = Future.successful(List(jsObj))

      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.insert(document) returns updateWriteResult

      val result = sessionController.sessionController.createSession(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "date" -> "2017-06-25",
          "session" -> "session 1",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo SEE_OTHER
    }

    "create session" in new WithApplication {
      val sessionController = testObject

      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")
      val _id: BSONObjectID = BSONObjectID.generate
      val document = SessionInfo("123", "test@example.com", date, "session 1", "topic", true, "", false, true, _id)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      /*val jsObj = JsObject(Map("_id" -> JsObject(Map("$oid" -> JsString("593938f42e00002e008516ee"))),
        "email" -> JsString("test@example.com"),
        "password" -> JsString("abc"),
        "algorithm" -> JsString("BCrypt"),
        "active" -> JsBoolean(true),
        "admin" -> JsBoolean(false)
      ))*/
      //val emailObject = Future.successful(List(jsObj))
      //val _id: BSONObjectID = BSONObjectID.generate
     // val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))
        val emailObject = Future.successful(List.empty)
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.insert(document) returns updateWriteResult

      val result = sessionController.sessionController.createSession(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "date" -> "2017-06-25",
          "session" -> "session 1",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo SEE_OTHER
    }


    "not create session when result is false" in new WithApplication {
      val sessionController = testObject

      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-25")

      val _id: BSONObjectID = BSONObjectID.generate
      val document = SessionInfo("123", "test@example.com", date, "session 1", "topic", true, "", false, true, _id)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      /*val jsObj = JsObject(Map("_id" -> JsObject(Map("$oid" -> JsString("593938f42e00002e008516ee"))),
        "email" -> JsString("test@example.com"),
        "password" -> JsString("abc"),
        "algorithm" -> JsString("BCrypt"),
        "active" -> JsBoolean(true),
        "admin" -> JsBoolean(false)
      ))*/

      //val emailObject = Future.successful(List(jsObj))
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.insert(document) returns updateWriteResult

      val result = sessionController.sessionController.createSession(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "date" -> "2017-06-25",
          "session" -> "session 1",
          "topic" -> "topic",
          "meetup" -> "true"))

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "not create session due to BadFormRequest" in new WithApplication {
      val sessionController = testObject

      /*val jsObj = JsObject(Map("_id" -> JsObject(Map("$oid" -> JsString("593938f42e00002e008516ee"))),
        "email" -> JsString("test@example.com"),
        "password" -> JsString("abc"),
        "algorithm" -> JsString("BCrypt"),
        "active" -> JsBoolean(true),
        "admin" -> JsBoolean(false)
      ))
      val emailObject = Future.successful(List(jsObj))*/
      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject =  Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false,_id)))

      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject

      val sessionDate = new Date(System.currentTimeMillis + 24 * 60 * 60 * 1000)
      val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val sessionDateString = simpleDateFormat.format(sessionDate)

      val result =
        sessionController.sessionController.createSession(FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "date" -> "25/06/2017",
            "session" -> "session 1",
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

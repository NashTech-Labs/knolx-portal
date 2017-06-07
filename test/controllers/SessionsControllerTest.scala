package controllers

import models.{SessionsRepository, UsersRepository}
import org.specs2.mock.Mockito
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsBoolean, JsObject, JsString}
import play.api.mvc.Result
import play.api.test.{FakeRequest, PlaySpecification, WithApplication}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONDocument

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionsControllerTest extends PlaySpecification with Mockito {

  val sessionObject = Future(List(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
    "UserId" -> JsString("user_id"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
    "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
    "Rating" -> JsString("rating"), "Active" -> JsString("Active")))))

  "Session Controller" should {
    "Display sessions" in {
      val sessionController = testObject
      sessionController.sessionsRepository.sessions returns sessionObject
      val result: Future[Result] = sessionController.sessionController.sessions(FakeRequest())
      contentAsString(result) must be contain "<th>Topic</th>"
      status(result) must be equalTo OK
    }

    "Manage sessions" in new WithApplication {
      val sessionController = testObject
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.sessions returns sessionObject
      val result = sessionController.sessionController.manageSessions(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))
      contentAsString(result) must be contain "<h3 class=\"panel-title\">Sessions</h3>"
      status(result) must be equalTo OK
    }

    "Manage session when wrong username is specified" in new WithApplication {
      val sessionController = testObject
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))
      sessionController.usersRepository.getByEmail("") returns Future(List())
      sessionController.sessionsRepository.sessions returns sessionObject
      val result = sessionController.sessionController.manageSessions(FakeRequest())
      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "Manage session when user is not admin" in new WithApplication {
      val sessionController = testObject
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.sessions returns sessionObject
      val result = sessionController.sessionController.manageSessions(FakeRequest().withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))
      contentAsString(result) must be contain ""
      status(result) must be equalTo UNAUTHORIZED
    }

    "Delete session" in new WithApplication {
      val sessionController = testObject
      val objectToDelete = Future(Some(JsObject(Seq("Email" -> JsString("email"), "Date" -> JsString("date"),
        "UserId" -> JsString("123"), "Session" -> JsString("sessions"), "Topic" -> JsString("topic"),
        "Meetup" -> JsString("meetup"), "Cancelled" -> JsString("cancelled"),
        "Rating" -> JsString("rating"), "Active" -> JsBoolean(true)))))
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.delete("123") returns objectToDelete
      val result = sessionController.sessionController.deleteSession("123")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))
      status(result) must be equalTo OK
    }

    "Unable to Delete session when wrong id is specified" in new WithApplication {
      val sessionController = testObject
      val objectToDelete = Future(None)
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.delete("1") returns objectToDelete
      val result = sessionController.sessionController.deleteSession("1")(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))
      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "Create Session Form" in new WithApplication {
      val sessionController = testObject
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      val result = sessionController.sessionController.create(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))
      status(result) must be equalTo OK
    }

    "Create Session" in new WithApplication {
      val sessionController = testObject
      val document = BSONDocument(
        "user_id" -> "123",
        "email" -> "test@example.com",
        "date" -> "6/7/2017",
        "session" -> "session",
        "topic" -> "topic",
        "meetup" -> "meetup",
        "rating" -> "",
        "cancelled" -> false,
        "active" -> true)

      val updateWriteResult = Future(UpdateWriteResult(true, 1, 1, Seq(), Seq(), None, None, None))
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true)))))
      sessionController.usersRepository.getByEmail("test@example.com") returns emailObject
      sessionController.sessionsRepository.create(document) returns updateWriteResult
      val result = sessionController.sessionController.createSession(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(("email", "email"), ("date", "date"), ("session", "session"),
          ("topic", "topic"), ("meetup", "meetup")))
      status(result) must be equalTo BAD_REQUEST
    }

    //WRITE TEST CASE FOR CORRECT FORM
    //WRITE TEST CASE WHEN RESULT IS FALSE
  }

  def testObject: TestObject = {
    val mockedSessionsRepository: SessionsRepository = mock[SessionsRepository]
    val mockedUsersRepository: UsersRepository = mock[UsersRepository]
    val mockedMessageApi: MessagesApi = mock[MessagesApi]
    val controller = new SessionsController(mockedMessageApi, mockedUsersRepository, mockedSessionsRepository)
    TestObject(mockedMessageApi, mockedUsersRepository, mockedSessionsRepository, controller)
  }

  case class TestObject(messagesApi: MessagesApi,
                        usersRepository: UsersRepository,
                        sessionsRepository: SessionsRepository,
                        sessionController: SessionsController)

}

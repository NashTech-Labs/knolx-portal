package controllers

import com.typesafe.config.ConfigFactory
import models.{UserInfo, UsersRepository}
import org.mockito.Matchers.{eq => eqTo}
import org.specs2.mock.Mockito
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, MessagesApi}
import play.api.test.{FakeRequest, PlaySpecification, WithApplication}
import play.api.{Configuration, Environment}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class UsersControllerSpec extends PlaySpecification with Mockito {

  "Users Controller" should {

    "render register form" in {
      val controller = testObject

      val result = controller.usersController.register(FakeRequest())

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "render login form" in {
      val controller = testObject

      val result = controller.usersController.login(FakeRequest())

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "logout user" in new WithApplication {
      val controller = testObject

      val result = controller.usersController.logout(FakeRequest().withSession("" -> ""))
      status(result) must be equalTo SEE_OTHER
    }

    "create user" in new WithApplication {
      val controller = testObject
      val document = UserInfo("usertest@example.com", "$2a$10$", "BCrypt", true, false)
      val emailObject = Future.successful(List.empty)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      controller.usersRepository.getByEmail("usertest@example.com") returns emailObject
      controller.usersRepository.insert(any[UserInfo])(any[ExecutionContext]) returns updateWriteResult
      val result = controller.usersController.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "usertest@example.com",
          "password" -> "12345678",
          "confirmPassword" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }

    "not create user due to some error" in new WithApplication {
      val controller = testObject

      val document = UserInfo("usertest@example.com", "$2a$10$", "BCrypt", true, false)

      val emailObject = Future.successful(List.empty)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      controller.usersRepository.getByEmail("usertest@example.com") returns emailObject

      controller.usersRepository.insert(any[UserInfo])(any[ExecutionContext]) returns updateWriteResult
      val result = controller.usersController.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "usertest@example.com",
          "password" -> "12345678",
          "confirmPassword" -> "12345678"))

      status(result) must be equalTo SEE_OTHER

    }

    "not create user when email already exists" in new WithApplication {
      val controller = testObject
      /*

            val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"),
              "admin" -> JsBoolean(false), "password" -> JsString("$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.")))))
      */

      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false, _id)))
      controller.usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.usersController.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678",
          "confirmPassword" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }

    "not create user due to BadFormRequest" in new WithApplication {
      val controller = testObject

      /*val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"),
        "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))*/

      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$", "BCrypt", true, false, _id)))
      controller.usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.usersController.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test",
          "password" -> "test@example.com",
          "confirmPassword" -> "usertest@example.com"))

      status(result) must be equalTo BAD_REQUEST
    }

    "login user when he is an admin" in new WithApplication {
      val controller = testObject

      /*val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"),
        "email" -> JsString("test@example.com"), "admin" -> JsBoolean(true),
        "password" -> JsString("$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.")))))*/

      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false, _id)))

      controller.usersRepository.getByEmail("test@example.com") returns emailObject
      val result = controller.usersController.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo SEE_OTHER

    }

    "login user when he is not an admin" in new WithApplication {
      val controller = testObject

      /*val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"),
        "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false),
        "password" -> JsString("$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.")))))*/
      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", true, false, _id)))

      controller.usersRepository.getByEmail("test@example.com") returns emailObject
      val result = controller.usersController.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo SEE_OTHER

    }


    "not login when user is not found" in new WithApplication {
      val controller = testObject

      val emailObject = Future.successful(List.empty)
      controller.usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.usersController.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo SEE_OTHER

    }

    "not login user when credentials are invalid" in new WithApplication {
      val controller = testObject

      /*val emailObject = Future.successful(List(JsObject(Seq("id" -> JsString("123"),
        "email" -> JsString("test@example.com"),
        "password" -> JsString("$2a$10$RdgzSPeWFo/jvadX3ykvGes1Y8OrY8HBqNExxeEoORoEEHEFeUnUG")))))*/
      val _id: BSONObjectID = BSONObjectID.generate
      val emailObject = Future.successful(List(UserInfo("usertest@example.com", "$2a$10$RdgzSPeWFo/jvadX3ykvGes1Y8OrY8HBqNExxeEoORoEEHEFeUnUG", "BCrypt", true, false, _id)))

      controller.usersRepository.getByEmail("test@example.com") returns emailObject
      val result = controller.usersController.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo UNAUTHORIZED
    }

    "not login user due to BadFormRequest" in new WithApplication {
      val controller = testObject

      val result = controller.usersController.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test",
          "password" -> "12345678"))

      status(result) must be equalTo BAD_REQUEST
    }
  }

  def testObject: TestObject = {
    val config = Configuration(ConfigFactory.load("application.conf"))
    val messages = new DefaultMessagesApi(Environment.simple(), config, new DefaultLangs(config))

    val mockedUsersRepository = mock[UsersRepository]

    val usersController = new UsersController(messages, mockedUsersRepository)

    TestObject(messages, mockedUsersRepository, usersController)
  }

  case class TestObject(messagesApi: MessagesApi,
                        usersRepository: UsersRepository,
                        usersController: UsersController)

}

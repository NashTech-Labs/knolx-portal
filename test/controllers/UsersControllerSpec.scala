package controllers

import com.google.inject.Module
import com.typesafe.config.ConfigFactory
import models.{UserInfo, UsersRepository}
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.ShouldThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test._
import play.api.{Application, Configuration, Environment}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UsersControllerSpec extends PlaySpecification with Mockito {

  abstract class WithTestApplication(val app: Application = GuiceApplicationBuilder().disable[Module].build()) extends Around
    with Scope with ShouldThrownExpectations with Mockito {
    override def around[T: AsResult](t: => T): Result = Helpers.running(app)(AsResult.effectively(t))

    val config = Configuration(ConfigFactory.load("application.conf"))
    val messages = new DefaultMessagesApi(Environment.simple(), config, new DefaultLangs(config))

    val usersRepository = mock[UsersRepository]

    val controller = new UsersController(messages, usersRepository)
  }

  private val _id: BSONObjectID = BSONObjectID.generate

  "Users Controller" should {

    "render register form" in new WithTestApplication {
      val result = controller.register(FakeRequest())

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "render login form" in new WithTestApplication {
      val result = controller.login(FakeRequest())

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "logout user" in new WithTestApplication {
      val result = controller.logout(FakeRequest().withSession("" -> ""))

      status(result) must be equalTo SEE_OTHER
    }

    "create user" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("usertest@example.com") returns emailObject
      usersRepository.insert(any[UserInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "usertest@example.com",
          "password" -> "12345678",
          "confirmPassword" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }

    "not create user due to some error" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("usertest@example.com") returns emailObject
      usersRepository.insert(any[UserInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "usertest@example.com",
          "password" -> "12345678",
          "confirmPassword" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }

    "not create user when email already exists" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678",
          "confirmPassword" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }
    "not create user due to BadFormRequest" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.createUser(FakeRequest(POST, "create")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test",
          "password" -> "test@example.com",
          "confirmPassword" -> "usertest@example.com"))

      status(result) must be equalTo BAD_REQUEST
    }

    "login user when he is an admin" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo SEE_OTHER

    }

    "login user when he is not an admin" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }


    "not login when user is not found" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)
      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }

    "not login user when credentials are invalid" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("usertest@example.com",
        "$2a$10$RdgzSPeWFo/jvadX3ykvGes1Y8OrY8HBqNExxeEoORoEEHEFeUnUG", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test@example.com",
          "password" -> "12345678"))

      status(result) must be equalTo UNAUTHORIZED
    }

    "not login user due to BadFormRequest" in new WithTestApplication {
      val result = controller.loginUser(FakeRequest()
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody("email" -> "test",
          "password" -> "12345678"))

      status(result) must be equalTo BAD_REQUEST
    }
  }

}

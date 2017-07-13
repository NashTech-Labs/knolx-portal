package controllers

import com.typesafe.config.ConfigFactory
import models.{UserInfo, UsersRepository}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.mvc.Results
import play.api.test.CSRFTokenHelper._
import play.api.test._
import play.api.{Application, Configuration}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UsersControllerSpec extends PlaySpecification with Results {

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }

    lazy val controller =
      new UsersController(knolxControllerComponent.messagesApi, usersRepository, config, knolxControllerComponent)
  }

  private val _id: BSONObjectID = BSONObjectID.generate

  "Users Controller" should {

    "render register form" in new WithTestApplication {
      val result = controller.register(FakeRequest().withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "render login form" in new WithTestApplication {
      val result = controller.login(FakeRequest().withCSRFToken)

      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "logout user" in new WithTestApplication {
      val result = controller.logout(FakeRequest().withSession("" -> "").withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "create user" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("usertest@example.com") returns emailObject
      usersRepository.insert(any[UserInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.createUser(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "usertest@example.com",
            "password" -> "12345678",
            "confirmPassword" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not create user due to some error" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("usertest@example.com") returns emailObject
      usersRepository.insert(any[UserInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.createUser(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "usertest@example.com",
            "password" -> "12345678",
            "confirmPassword" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not create user when email already exists" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.createUser(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "12345678",
            "confirmPassword" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not create user due to BadFormRequest" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.createUser(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test",
            "password" -> "test@example.com",
            "confirmPassword" -> "usertest@example.com")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not create user when password length is less than 8" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.createUser(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "test",
            "confirmPassword" -> "test")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "not create user when password and confirm password does not match" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com", "$2a$10$", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.createUser(
        FakeRequest(POST, "create")
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "test1234",
            "confirmPassword" -> "test12345")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "login user when he is an admin" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=",
            "admin" -> "DqDK4jVae2aLvChuBPCgmfRWXKArji6AkjVhqSxpMFP6I6L/FkeK5HQz1dxzxzhP")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "login user when he is not an admin" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("test@example.com",
        "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not login when user is not found" in new WithTestApplication {
      val emailObject = Future.successful(List.empty)
      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not login user when credentials are invalid" in new WithTestApplication {
      val emailObject = Future.successful(List(UserInfo("usertest@example.com",
        "$2a$10$RdgzSPeWFo/jvadX3ykvGes1Y8OrY8HBqNExxeEoORoEEHEFeUnUG", "BCrypt", active = true, admin = false, _id)))

      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo UNAUTHORIZED
    }

    "not login user due to BadFormRequest" in new WithTestApplication {
      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test",
            "password" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }
  }

}

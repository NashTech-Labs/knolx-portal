package controllers

import java.text.SimpleDateFormat
import java.time.{Instant, LocalTime, ZoneId}
import java.util.TimeZone

import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.libs.mailer.MailerClient
import play.api.mvc.Results
import play.api.test.CSRFTokenHelper._
import play.api.test._
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONObjectID
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UsersControllerSpec extends PlaySpecification with Results {

  private val emptyEmailObject = Future.successful(None)
  private val _id: BSONObjectID = BSONObjectID.generate
  private val emailObject = Future.successful(Some(UserInfo("test@example.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, _id)))
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  private val ISTZoneId = ZoneId.of("Asia/Kolkata")

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp
    val forgotPasswordRepository = mock[ForgotPasswordRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val mailerClient = mock[MailerClient]
    lazy val controller =
      new UsersController(knolxControllerComponent.messagesApi,
        usersRepository,
        forgotPasswordRepository,
        config,
        dateTimeUtility,
        knolxControllerComponent,
        mailerClient)

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Users Controller" should {

/*    "render register form" in new WithTestApplication {
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

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("usertest@example.com") returns emptyEmailObject
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

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("usertest@example.com") returns emptyEmailObject
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

      usersRepository.getActiveByEmail("test@example.com") returns emailObject

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

      usersRepository.getActiveByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not login when user is not found" in new WithTestApplication {

      usersRepository.getActiveByEmail("test@example.com") returns emptyEmailObject

      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "12345678")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "not login user when credentials are invalid" in new WithTestApplication {
      usersRepository.getActiveByEmail("test@example.com") returns emailObject

      val result = controller.loginUser(
        FakeRequest()
          .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
          .withFormUrlEncodedBody("email" -> "test@example.com",
            "password" -> "123456789")
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

    "render manage user page" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.paginate(1, Some("test@example.com")) returns emailObject.map(user => List(user.get))
      usersRepository.userCountWithKeyword(Some("test@example.com")) returns Future.successful(1)

      val result = controller.manageUser(1, Some("test@example.com"))(FakeRequest(GET, "search")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=").withCSRFToken)

      status(result) must be equalTo OK
    }

    "return json for the user searched by email" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.paginate(1, Some("test@example.com")) returns emailObject.map(user => List(user.get))
      usersRepository.userCountWithKeyword(Some("test@example.com")) returns Future.successful(1)

      val result = controller.searchUser()(FakeRequest(POST, "search")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "page" -> "1"))

      status(result) must be equalTo OK
    }

    "throw a bad request when encountered a invalid value for search user form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      val result = controller.searchUser()(FakeRequest(POST, "search")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "page" -> "invalid value").withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "throw a bad request when encountered a invalid value for getByEmail user form" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      val result = controller.updateUser()(FakeRequest(POST, "getByEmail")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "active" -> "invalid value",
          "password" -> "12345678").withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "redirect to manage user page on successful submission of form" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.update(UpdatedUserInfo("test@example.com", active = true, Some("12345678"))) returns updateWriteResult

      val result = controller.updateUser()(FakeRequest(POST, "getByEmail")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "active" -> "true",
          "password" -> "12345678"))

      status(result) must be equalTo SEE_OTHER
    }

    "throw internal server error while updating user information to database" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.update(UpdatedUserInfo("test@example.com", active = true, Some("12345678"))) returns updateWriteResult

      val result = controller.updateUser()(FakeRequest(POST, "getByEmail")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com",
          "active" -> "true",
          "password" -> "12345678"))

      status(result) must be equalTo INTERNAL_SERVER_ERROR
    }

    "render getByEmail user page with form " in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject

      val result = controller.getByEmail("test@example.com")(FakeRequest(GET, "updatePage")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=").withCSRFToken)

      status(result) must be equalTo OK
    }

    "redirect to manages session page if user to getByEmail no found" in new WithTestApplication {

      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.getByEmail("user@example.com") returns Future.successful(None)

      val result = controller.getByEmail("user@example.com")(FakeRequest(GET, "updatePage")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo SEE_OTHER
    }


    "redirect to manage user on successful delete" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.delete("test@example.com") returns emailObject.map(user => Some(user.get))

      val result = controller.deleteUser("test@example.com")(FakeRequest(GET, "updatePage")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo SEE_OTHER
    }

    "redirect to manage user on failure during delete, with appropriate message" in new WithTestApplication {
      usersRepository.getByEmail("test@example.com") returns emailObject
      usersRepository.delete("user@example.com") returns Future.successful(None)

      val result = controller.deleteUser("user@example.com")(FakeRequest(GET, "updatePage")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="))

      status(result) must be equalTo SEE_OTHER
    }*/

    "render forgot password page" in new WithTestApplication {

      val result = controller.forgotPassword(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }

    "restrict sending an email with the password reset link to the user requested for the password change if invalid email" in new WithTestApplication {

      usersRepository.getActiveByEmail("test@example.com") returns emptyEmailObject

      val result = controller.requestPasswordChange()(FakeRequest(POST, "password/change")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "").withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "restrict sending an email with the password reset link to the user requested for " +
      "the password change if user not exists" in new WithTestApplication {

      usersRepository.getActiveByEmail("test@example.com") returns emptyEmailObject

      val result = controller.requestPasswordChange()(FakeRequest(POST, "password/change")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com").withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

    "send an email with the password reset link to the user requested"in new WithTestApplication {

      usersRepository.getActiveByEmail("test@example.com") returns emailObject
      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTime= Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime
      dateTimeUtility.localDateTimeIST returns localDateTime
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      mailerClient.send("dd") returns "sent"
      val result = controller.requestPasswordChange()(FakeRequest(POST, "password/change")
        .withSession("username" -> "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")
        .withFormUrlEncodedBody(
          "email" -> "test@example.com").withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }
  }

}

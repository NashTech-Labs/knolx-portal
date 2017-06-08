package controllers

import models.UsersRepository
import org.specs2.mock.Mockito
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsBoolean, JsObject, JsString}
import play.api.test.{FakeRequest, PlaySpecification, WithApplication}
import reactivemongo.bson.BSONDocument
import utilities.PasswordUtility

import scala.concurrent.Future


class UsersControllerSpec extends PlaySpecification with Mockito {

  "Users Controller" should {
    "Register" in {
      val controller = testObject
      val result = controller.usersController.register(FakeRequest())
      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "Login" in {
      val controller = testObject
      val result = controller.usersController.login(FakeRequest())
      contentAsString(result) must be contain ""
      status(result) must be equalTo OK
    }

    "Logout" in new WithApplication {
      val controller = testObject
      val result = controller.usersController.logout(FakeRequest().withSession(""->""))
      status(result) must be equalTo SEE_OTHER
    }

  /*  "Create User" in {
      val controller = testObject
      val document = new BSONDocument(
        "email" -> "test@example.com",
        "password" -> PasswordUtility.encrypt(userInfo.password),
        "algorithm" -> PasswordUtility.BCrypt,
        "active" -> true,
        "admin" -> false)
      val emailObject = Future(List(JsObject(Seq("id" -> JsString("123"), "email" -> JsString("test@example.com"), "admin" -> JsBoolean(false)))))
      controller.usersRepository.getByEmail("test@example.com") returns emailObject
    }*/

  }

  def testObject: TestObject = {
    val mockedMessagesApi = mock[MessagesApi]
    val mockedUsersRepository = mock[UsersRepository]
    val usersController = new UsersController(mockedMessagesApi, mockedUsersRepository)
    TestObject(mockedMessagesApi, mockedUsersRepository, usersController)
  }

  case class TestObject(messagesApi: MessagesApi,
                        usersRepository: UsersRepository,
                        usersController: UsersController)
}

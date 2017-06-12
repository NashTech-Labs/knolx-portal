package models

import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString}
import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext.Implicits.global

class UsersRepositorySpec extends PlaySpecification {

  val usersRepository = new UsersRepository(TestDb.reactiveMongoApi)

  "Users Repository" should {

    "create user" in {
      val document = UserInfo("test@example.com", "password", "encryptedpassword", active = true, admin = false)

      val created = await(usersRepository.insert(document).map(_.ok))

      created must beEqualTo(true)
    }

    "get user by email" in {
      val user = await(usersRepository.getByEmail("test@example.com"))
      val head = user.headOption.getOrElse(JsObject(Seq.empty))

      (head \ "email").getOrElse(JsString("")) must beEqualTo(JsString("test@example.com"))
      (head \ "password").getOrElse(JsNumber(0)) must beEqualTo(JsString("password"))
      (head \ "active").getOrElse(JsBoolean(false)) must beEqualTo(JsBoolean(true))
    }

  }

}

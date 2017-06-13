package models

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
      val elseUser = UserInfo("", "", "", active = false, admin = false)
      val head = user.headOption.getOrElse(elseUser)
      head.email must beEqualTo("test@example.com")
      head.password must beEqualTo("password")
      head.active must beEqualTo(true)
    }

  }

}

package models

import play.api.test.PlaySpecification
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

class UsersRepositorySpec extends PlaySpecification {

  val usersRepository = new UsersRepository(TestDb.reactiveMongoApi)
  val updateWriteResult: UpdateWriteResult = UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None)
  val document = UserInfo("test@example.com", "password", "encryptedpassword", active = true, admin = false)

  "Users Repository" should {

    "create user" in {
      val created = await(usersRepository.insert(document).map(_.ok))

      created must beEqualTo(true)
    }

    "get user by email" in {
      val user = await(usersRepository.getByEmail("test@example.com"))

      user must beEqualTo(Some(document))

    }

    "get paginated user when searched with empty string" in {
      val paginatedUsers = await(usersRepository.paginate(1))

      paginatedUsers must beEqualTo(List(document))
    }

    "get paginated user when searched with some string" in {
      val paginatedUsers = await(usersRepository.paginate(1, Some("test")))

      paginatedUsers must beEqualTo(List(document))
    }

    "update user with password change " in {
      val userTOUpdate = UpdatedUserInfo("test@example.com", active = true, Some("12345678"))

      val result = await(usersRepository.update(userTOUpdate))

      result must beEqualTo(updateWriteResult)

    }

    "update user with no password change " in {
      val userTOUpdate = UpdatedUserInfo("test@example.com", active = false, None)

      val result = await(usersRepository.update(userTOUpdate))

      result must beEqualTo(updateWriteResult)
    }

    "get active user count when serched with empty string" in {
      val count = await(usersRepository.userCountWithKeyword(None))

      count must beEqualTo(1)
    }

    "get active user count when serched with some string" in {
      val count = await(usersRepository.userCountWithKeyword(Some("test")))

      count must beEqualTo(1)
    }

    "delete user by email" in {
      val expectedOutput = UserInfo("test@example.com", "12345678", "encryptedpassword", active = false, admin = false)

      val result = await(usersRepository.delete("test@example.com"))

      result.get.email must beEqualTo("test@example.com")
    }

  }

}

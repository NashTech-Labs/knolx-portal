package models

import java.text.SimpleDateFormat

import org.specs2.mock.Mockito
import play.api.test.PlaySpecification
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global

class UsersRepositorySpec extends PlaySpecification with Mockito {

  val dateTimeUtility: DateTimeUtility = mock[DateTimeUtility]
  val usersRepository = new UsersRepository(TestDb.reactiveMongoApi, dateTimeUtility)
  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  private val dateString = "2016-07-12T14:30:00"
  private val date = formatter.parse(dateString)
  private val millis = date.getTime
  private val currentMillis = formatter.parse("2017-07-12T14:30:00").getTime
  val updateWriteResult: UpdateWriteResult = UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None)
  val document = UserInfo("test@example.com", "password", "encryptedpassword", active = true, admin = false, BSONDateTime(millis))

  "Users Repository" should {

    "create user" in {
      val created = await(usersRepository.insert(document).map(_.ok))

      created must beEqualTo(true)
    }

    "get user by email" in {
      val user = await(usersRepository.getByEmail("test@example.com"))

      user must beEqualTo(Some(document))
    }

    "get active user by email" in {
      dateTimeUtility.nowMillis returns currentMillis
      val user = await(usersRepository.getActiveByEmail("test@example.com"))

      user must beEqualTo(Some(document))
    }

    "get active and unbanned users by email" in {
      dateTimeUtility.nowMillis returns currentMillis
      val user = await(usersRepository.getActiveAndUnbanned("test@example.com"))

      user must beEqualTo(Some(document))
    }

    "get paginated user when searched with empty string and `all` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, None))

      paginatedUsers must beEqualTo(List(document))
    }
    "get paginated user when searched with empty string and `banned` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, None, "banned"))

      paginatedUsers must beEqualTo(Nil)
    }
    "get paginated user when searched with empty string and `allowed` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, None, "allowed"))

      paginatedUsers must beEqualTo(List(document))
    }
    "get paginated user when searched with empty string and `active` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, None, "active"))

      paginatedUsers must beEqualTo(List(document))
    }
    "get paginated user when searched with empty string and `suspended` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, None, "suspended"))

      paginatedUsers must beEqualTo(Nil)
    }

    "get all paginated user when searched with empty string and invalid filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, None, "invalid"))

      paginatedUsers must beEqualTo(List(document))
    }

    "get paginated user when searched with some string and `all` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, Some("test")))

      paginatedUsers must beEqualTo(List(document))
    }

    "get paginated user when searched with some string and `banned` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, Some("test"), "banned"))

      paginatedUsers must beEqualTo(Nil)
    }

    "get paginated user when searched with some string and `allowed` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, Some("test"), "allowed"))

      paginatedUsers must beEqualTo(List(document))
    }

    "get paginated user when searched with some string and `active` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, Some("test"), "active"))

      paginatedUsers must beEqualTo(List(document))
    }

    "get paginated user when searched with some string and `suspended` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val paginatedUsers = await(usersRepository.paginate(1, Some("test"), "suspended"))

      paginatedUsers must beEqualTo(Nil)
    }

    "getByEmail user with password change " in {
      val userTOUpdate = UpdatedUserInfo("test@example.com", active = true, Some("12345678"))

      val result = await(usersRepository.update(userTOUpdate))

      result must beEqualTo(updateWriteResult)
    }

    "getByEmail user with no password change " in {
      val userTOUpdate = UpdatedUserInfo("test@example.com", active = false, None)

      val result = await(usersRepository.update(userTOUpdate))

      result must beEqualTo(updateWriteResult)
    }

    "get user count when searched with empty string and `all` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(None))

      count must beEqualTo(1)
    }

    "get user count when searched with empty string and `banned` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(None, "banned"))

      count must beEqualTo(0)
    }

    "get user count when searched with empty string and `allowed` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(None, "allowed"))

      count must beEqualTo(1)
    }

    "get user count when searched with empty string and `active` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(None, "active"))

      count must beEqualTo(0)
    }

    "get user count when searched with empty string and `suspended` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(None, "suspended"))

      count must beEqualTo(1)
    }

    "get all user count when searched with empty string and an invalid filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(None, "invalid"))

      count must beEqualTo(1)
    }

    "get active user count when searched with some string and `all` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(Some("test")))

      count must beEqualTo(1)
    }

    "get active user count when searched with some string and `banned` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(Some("test"), "banned"))

      count must beEqualTo(0)
    }

    "get active user count when searched with some string and `allowed` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(Some("test"), "allowed"))

      count must beEqualTo(1)
    }

    "get active user count when searched with some string and `active` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(Some("test"), "active"))

      count must beEqualTo(0)
    }

    "get active user count when searched with some string and `suspended` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(Some("test"), "suspended"))

      count must beEqualTo(1)
    }

    "get active user count when searched with some string" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(Some("test")))

      count must beEqualTo(1)
    }

    "delete user by email" in {
      val result = await(usersRepository.delete("test@example.com"))

      result.get.email must beEqualTo("test@example.com")
    }

  }

}

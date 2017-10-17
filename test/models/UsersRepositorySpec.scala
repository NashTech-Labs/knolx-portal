package models

import java.text.SimpleDateFormat
import java.time.{Instant, ZoneId}
import java.util.TimeZone

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
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val currentMillis = formatter.parse("2017-07-12T14:30:00").getTime
  val updateWriteResult: UpdateWriteResult = UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None)
  val document = UserInfo("test@knoldus.com", "password", "encryptedpassword", active = true, admin = false, coreMember = false, superUser = false, BSONDateTime(millis))

  "Users Repository" should {

    "create user" in {
      val created = await(usersRepository.insert(document).map(_.ok))

      created must beEqualTo(true)
    }

    "get user by email" in {
      val user = await(usersRepository.getByEmail("test@knoldus.com"))

      user must beEqualTo(Some(document))
    }

    "get active user by email" in {
      dateTimeUtility.nowMillis returns currentMillis
      val user = await(usersRepository.getActiveByEmail("test@knoldus.com"))

      user must beEqualTo(Some(document))
    }

    "get active and unbanned users by email" in {
      dateTimeUtility.nowMillis returns currentMillis
      val user = await(usersRepository.getActiveAndBanned("test@knoldus.com"))

      user must beEqualTo(None)
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

    "getByEmail user with password change and not banned" in {
      val userTOUpdate = UpdatedUserInfo("test@knoldus.com", active = true, ban = false, coreMember = false, admin= false, Some("12345678"))

      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTime = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime
      dateTimeUtility.localDateTimeIST returns localDateTime
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      dateTimeUtility.nowMillis returns currentMillis
      dateTimeUtility.toLocalDateTime(dateTimeUtility.nowMillis) returns localDateTime

      val result = await(usersRepository.update(userTOUpdate))

      result must beEqualTo(updateWriteResult)
    }

    "getByEmail user with no password change and not banned" in {
      val userTOUpdate = UpdatedUserInfo("test@knoldus.com", active = false, ban = false, coreMember = false, admin= false, None)

      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTime = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime
      dateTimeUtility.localDateTimeIST returns localDateTime
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      dateTimeUtility.nowMillis returns currentMillis
      dateTimeUtility.toLocalDateTime(dateTimeUtility.nowMillis) returns localDateTime

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

      count must beEqualTo(1)
    }

    "get user count when searched with empty string and `allowed` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(None, "allowed"))

      count must beEqualTo(0)
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

      count must beEqualTo(1)
    }

    "get active user count when searched with some string and `allowed` filter" in {
      dateTimeUtility.nowMillis returns currentMillis
      val count = await(usersRepository.userCountWithKeyword(Some("test"), "allowed"))

      count must beEqualTo(0)
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
      val result = await(usersRepository.delete("test@knoldus.com"))

      result.get.email must beEqualTo("test@knoldus.com")
    }

    "getByEmail user with no password change and banned" in {
      val userTOUpdate = UpdatedUserInfo("test@knoldus.com", active = false, ban = true, coreMember = false, admin= false, None)

      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTime = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime
      dateTimeUtility.localDateTimeIST returns localDateTime
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      dateTimeUtility.nowMillis returns currentMillis
      dateTimeUtility.toLocalDateTime(dateTimeUtility.nowMillis) returns localDateTime

      val result = await(usersRepository.update(userTOUpdate))

      result must beEqualTo(updateWriteResult)
    }

    "getByEmail user with password change and  banned" in {
      val userTOUpdate = UpdatedUserInfo("test@knoldus.com", active = true, ban = true, coreMember = false, admin= false, Some("12345678"))

      val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse("2017-06-25T16:00")
      val localDateTime = Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime
      dateTimeUtility.localDateTimeIST returns localDateTime
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      dateTimeUtility.nowMillis returns currentMillis
      dateTimeUtility.toLocalDateTime(dateTimeUtility.nowMillis) returns localDateTime

      val result = await(usersRepository.update(userTOUpdate))

      result must beEqualTo(updateWriteResult)
    }

    "update the password" in {
      val userTOUpdate = UpdatedUserInfo("test@knoldus.com", active = true, ban = false, coreMember = false, admin = false, Some("12345678"))

      val result = await(usersRepository.updatePassword(userTOUpdate))

      result must beEqualTo(updateWriteResult)
    }

    "not update the password" in {

      val updateWriteResultWithFalse: UpdateWriteResult = UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None)

      val userTOUpdate = UpdatedUserInfo("test@knoldus.com", active = true, ban = false, coreMember = false, admin = false, None)

      val result = await(usersRepository.updatePassword(userTOUpdate))

      result.ok must beEqualTo(updateWriteResultWithFalse.ok)
    }

  }

}

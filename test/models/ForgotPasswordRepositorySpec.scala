package models

import java.text.SimpleDateFormat

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility
import scala.concurrent.ExecutionContext.Implicits.global

class ForgotPasswordRepositorySpec extends PlaySpecification with Mockito{

  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  private val currentDateString = "2017-07-12T14:30:00"
  private val currentDate = formatter.parse(currentDateString)
  private val currentMillis = currentDate.getTime

  trait TestScope extends Scope {
    val dateTimeUtility: DateTimeUtility = mock[DateTimeUtility]

    val forgotPasswordRepository = new ForgotPasswordRepository(TestDb.reactiveMongoApi, dateTimeUtility)
  }

  "Forgot password repository" should {

    "upsert user record with new password change request" in new TestScope {
      val passwordChangeRequest= PasswordChangeRequestInfo("test@example.com", "token", BSONDateTime(currentMillis + 24 * 60 * 60 * 1000))
      val created: Boolean = await(forgotPasswordRepository.upsert(passwordChangeRequest).map(_.ok))

      created must beEqualTo(true)
    }

    "fetch record for password change request with token only" in new TestScope {
      val passwordChangeRequest= PasswordChangeRequestInfo("test@example.com", "token", BSONDateTime(currentMillis + 24 * 60 * 60 * 1000))
      val record: Option[PasswordChangeRequestInfo] = await(forgotPasswordRepository.getPasswordChangeRequest("token", None))

      record.get must beEqualTo(passwordChangeRequest)
    }

    "fetch record for password change request with token and email" in new TestScope {
      val passwordChangeRequest= PasswordChangeRequestInfo("test@example.com", "token", BSONDateTime(currentMillis + 24 * 60 * 60 * 1000))
      val record: Option[PasswordChangeRequestInfo] = await(forgotPasswordRepository.getPasswordChangeRequest("token", Some("test@example.com")))

      record.get must beEqualTo(passwordChangeRequest)
    }

  }

}

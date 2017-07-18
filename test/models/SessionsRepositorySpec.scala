package models

import java.text.SimpleDateFormat

import controllers.UpdateSessionInformation
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{JsBoolean, JsObject}
import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global

class SessionsRepositorySpec extends PlaySpecification with Mockito {

  private val sessionId = BSONObjectID.generate

  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

  private val currentDateString = "2017-07-12T14:30:00"
  private val currentDate = formatter.parse(currentDateString)
  private val currentMillis = currentDate.getTime

  private val startOfDayDateString = "2017-07-12T00:00:00"
  private val startOfDayDate = formatter.parse(startOfDayDateString)
  private val startOfDayMillis = startOfDayDate.getTime

  private val endOfDayDateString = "2017-07-12T23:59:59"
  private val endOfDayDate = formatter.parse(endOfDayDateString)
  private val endOfDayMillis = endOfDayDate.getTime

  val sessionInfo = SessionInfo("testId1", "test@example.com", BSONDateTime(currentMillis), "session1", "feedbackFormId", "topic1",
    1, meetup = true, "", cancelled = false, active = true, BSONDateTime(currentMillis + 24 * 60 * 60 * 1000), sessionId)

  trait TestScope extends Scope {
    val dateTimeUtility = mock[DateTimeUtility]

    val sessionsRepository = new SessionsRepository(TestDb.reactiveMongoApi, dateTimeUtility)
  }

  "Session repository" should {

    "insert session" in new TestScope {
      val created = await(sessionsRepository.insert(sessionInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get sessions" in new TestScope {
      val sessions = await(sessionsRepository.sessions)

      sessions must beEqualTo(List(sessionInfo))
    }

    "get session by id" in new TestScope {
      val maybeSession = await(sessionsRepository.getById(sessionId.stringify))

      maybeSession contains sessionInfo
    }

    "get sessions scheduled today" in new TestScope {
      dateTimeUtility.startOfDayMillis returns startOfDayMillis
      dateTimeUtility.endOfDayMillis returns endOfDayMillis

      val sessions = await(sessionsRepository.sessionsScheduledToday)

      sessions must beEqualTo(List(sessionInfo))
    }

    "update session" in new TestScope {
      val updatedSession = UpdateSessionInfo(UpdateSessionInformation(sessionId.stringify, currentDate,
        "updatedSession", "feedbackFormId", "updatedTopic", 1), BSONDateTime(currentMillis + 24 * 60 * 60 * 1000))

      val updated = await(sessionsRepository.update(updatedSession).map(_.ok))

      updated must beEqualTo(true)
    }


    "get paginated sessions when serched with empty string" in new TestScope {
      val paginatedSessions = await(sessionsRepository.paginate(1))

      paginatedSessions must beEqualTo(List(sessionInfo.copy(session = "updatedSession", topic = "updatedTopic", meetup = false)))
    }

    "get paginated sessions when serched with some string" in new TestScope {
      val paginatedSessions = await(sessionsRepository.paginate(1,Some("test")))

      paginatedSessions must beEqualTo(List(sessionInfo.copy(session = "updatedSession", topic = "updatedTopic", meetup = false)))
    }

    "get active sessions count when serched with empty string" in new TestScope {
      val count = await(sessionsRepository.activeCount(None))

      count must beEqualTo(1)
    }

    "get active sessions count when serched with some string" in new TestScope {
      val count = await(sessionsRepository.activeCount(Some("test")))

      count must beEqualTo(1)
    }

    "delete session" in new TestScope {
      val deletedSession = await(sessionsRepository.delete(sessionId.stringify))

      val deletedSessionJsObject = deletedSession.getOrElse(JsObject(Seq.empty))

      (deletedSessionJsObject \ "active").getOrElse(JsBoolean(true)) must beEqualTo(JsBoolean(false))
    }

    "get active sessions" in new TestScope {
      val sessionId = BSONObjectID.generate
      val sessionInfo = SessionInfo("testId2", "test@example.com", BSONDateTime(currentMillis), "session2", "feedbackFormId", "topic2",
        1, meetup = true, "", cancelled = false, active = true, BSONDateTime(currentMillis + 24 * 60 * 60 * 1000), sessionId)

      val created = await(sessionsRepository.insert(sessionInfo).map(_.ok))
      created must beEqualTo(true)

      val greaterThanSessionMillis = currentMillis + 2 * 60 * 60 * 1000

      dateTimeUtility.nowMillis returns greaterThanSessionMillis

      val activeSessions = await(sessionsRepository.activeSessions)

      activeSessions must beEqualTo(List(sessionInfo))
    }

    "get immediate previous expired sessions" in new TestScope {
      val sessionId = BSONObjectID.generate
      val sessionInfo = SessionInfo("testId2", "test@example.com", BSONDateTime(currentMillis), "session2", "feedbackFormId", "topic2",
        1, meetup = true, "", cancelled = false, active = true, BSONDateTime(currentMillis + 23 * 60 * 60 * 1000), sessionId)

      val created = await(sessionsRepository.insert(sessionInfo).map(_.ok))
      created must beEqualTo(true)

      val greaterThanSessionExpirationMillis = currentMillis + 24 * 60 * 60 * 1000

      dateTimeUtility.nowMillis returns greaterThanSessionExpirationMillis

      val expiredSessions = await(sessionsRepository.immediatePreviousExpiredSessions)

      expiredSessions must beEqualTo(List(sessionInfo))
    }

  }

}

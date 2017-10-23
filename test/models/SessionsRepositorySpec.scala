package models

import java.text.SimpleDateFormat

import controllers.UpdateSessionInformation
import models.SessionJsonFormats.{ExpiringNext, ExpiringNextNotReminded, SchedulingNext, SchedulingNextUnNotified}
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{JsBoolean, JsObject}
import play.api.test.PlaySpecification
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global

class SessionsRepositorySpec extends PlaySpecification with Mockito {

  private val sessionId = BSONObjectID.generate
  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  private val currentDateString = "2017-07-12T14:30:00"
  private val currentDate = formatter.parse(currentDateString)
  private val currentMillis = currentDate.getTime
  private val endOfDayDateString = "2017-07-12T23:59:59"
  private val endOfDayDate = formatter.parse(endOfDayDateString)
  private val endOfDayMillis = endOfDayDate.getTime

  val sessionInfo = SessionInfo("testId1", "test@example.com", BSONDateTime(currentMillis), "session1", "feedbackFormId", "topic1",
    1, meetup = true, "", 0.00, cancelled = false, active = true, BSONDateTime(currentMillis + 24 * 60 * 60 * 1000), Some("youtubeURL"), Some("slideShareURL"), 0, reminder = false, notification = false, sessionId)

  trait TestScope extends Scope {
    val dateTimeUtility: DateTimeUtility = mock[DateTimeUtility]

    val sessionsRepository = new SessionsRepository(TestDb.reactiveMongoApi, dateTimeUtility)
  }

  "Session repository" should {

    "insert session" in new TestScope {
      val created: Boolean = await(sessionsRepository.insert(sessionInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get sessions" in new TestScope {
      val sessions: List[SessionInfo] = await(sessionsRepository.sessions)

      sessions must beEqualTo(List(sessionInfo))
    }

    "get session by id" in new TestScope {
      val maybeSession: Option[SessionInfo] = await(sessionsRepository.getById(sessionId.stringify))

      maybeSession contains sessionInfo
    }

    "get sessions scheduled next" in new TestScope {
      dateTimeUtility.nowMillis returns currentMillis
      dateTimeUtility.endOfDayMillis returns endOfDayMillis

      val sessions: List[SessionInfo] = await(sessionsRepository.sessionsForToday(SchedulingNext))

      sessions must beEqualTo(List(sessionInfo))
    }

    "get sessions expiring next not reminded" in new TestScope {
      dateTimeUtility.nowMillis returns currentMillis
      dateTimeUtility.endOfDayMillis returns endOfDayMillis

      val sessions: List[SessionInfo] = await(sessionsRepository.sessionsForToday(ExpiringNextNotReminded))

      sessions must beEqualTo(Nil)
    }

    "get sessions scheduled next unnotified" in new TestScope {
      dateTimeUtility.nowMillis returns currentMillis
      dateTimeUtility.endOfDayMillis returns endOfDayMillis

      val sessions: List[SessionInfo] = await(sessionsRepository.sessionsForToday(SchedulingNextUnNotified))

      sessions must beEqualTo(List(sessionInfo))
    }

    "get sessions expiring next" in new TestScope {
      dateTimeUtility.startOfDayMillis returns currentMillis
      dateTimeUtility.endOfDayMillis returns endOfDayMillis

      val sessions: List[SessionInfo] = await(sessionsRepository.sessionsForToday(ExpiringNext))

      sessions must beEqualTo(Nil)
    }


    "getByEmail session" in new TestScope {
      val updatedSession = UpdateSessionInfo(UpdateSessionInformation(sessionId.stringify, currentDate,
        "updatedSession", "feedbackFormId", "updatedTopic", 1, Some("youtubeURL"), Some("slideShareURL")), BSONDateTime(currentMillis + 24 * 60 * 60 * 1000))

      val updated: Boolean = await(sessionsRepository.update(updatedSession).map(_.ok))

      updated must beEqualTo(true)
    }

    "get active session by id" in new TestScope {
      val response = await(sessionsRepository.getActiveById(sessionId.stringify))

      response contains sessionInfo
    }

    "get paginated sessions when serched with empty string" in new TestScope {
      val paginatedSessions: List[SessionInfo] = await(sessionsRepository.paginate(1))

      paginatedSessions must beEqualTo(List(sessionInfo.copy(session = "updatedSession", topic = "updatedTopic", meetup = false)))
    }

    "get paginated sessions when serched with some string" in new TestScope {
      val paginatedSessions: List[SessionInfo] = await(sessionsRepository.paginate(1, Some("test")))

      paginatedSessions must beEqualTo(List(sessionInfo.copy(session = "updatedSession", topic = "updatedTopic", meetup = false)))
    }

    "get active sessions count when serched with empty string" in new TestScope {
      val count: Int = await(sessionsRepository.activeCount(None))

      count must beEqualTo(1)
    }

    "get users session till now for a particular user" in new TestScope {
      val response = await(sessionsRepository.userSessionsTillNow(Some("test@example.com")))

      response contains sessionInfo
    }

    "get users session till now for all users" in new TestScope {
      val response = await(sessionsRepository.userSessionsTillNow(None))

      response contains sessionInfo
    }

    "get active sessions count when serched with some string" in new TestScope {
      val count: Int = await(sessionsRepository.activeCount(Some("test")))

      count must beEqualTo(1)
    }

    "delete session" in new TestScope {
      val deletedSession: Option[JsObject] = await(sessionsRepository.delete(sessionId.stringify))

      val deletedSessionJsObject: JsObject = deletedSession.getOrElse(JsObject(Seq.empty))

      (deletedSessionJsObject \ "active").getOrElse(JsBoolean(true)) must beEqualTo(JsBoolean(false))
    }

    "get active sessions" in new TestScope {
      val sessionId: BSONObjectID = BSONObjectID.generate
      val sessionInfo = SessionInfo("testId2", "test@example.com", BSONDateTime(currentMillis), "session2", "feedbackFormId", "topic2",
        1, meetup = true, "", 0, cancelled = false, active = true, BSONDateTime(currentMillis + 24 * 60 * 60 * 1000), Some("youtubeURL"), Some("slideShareURL"), 0, reminder = false, notification = false, sessionId)

      val created: Boolean = await(sessionsRepository.insert(sessionInfo).map(_.ok))
      created must beEqualTo(true)

      val greaterThanSessionMillis: Long = currentMillis + 2 * 60 * 60 * 1000

      dateTimeUtility.nowMillis returns greaterThanSessionMillis

      val activeSessions: List[SessionInfo] = await(sessionsRepository.activeSessions())

      activeSessions must beEqualTo(List(sessionInfo))
    }

    "get active sessions by email" in new TestScope {
      val sessionId: BSONObjectID = BSONObjectID.generate
      val sessionInfo = SessionInfo("testId2", "test@example.com", BSONDateTime(currentMillis), "session2", "feedbackFormId", "topic2",
        1, meetup = true, "", 0.00, cancelled = false, active = true, BSONDateTime(currentMillis + 24 * 60 * 60 * 1000), Some("youtubeURL"), Some("slideShareURL"), 0, reminder = false, notification = false, sessionId)

      val created: Boolean = await(sessionsRepository.insert(sessionInfo).map(_.ok))
      created must beEqualTo(true)

      val greaterThanSessionMillis: Long = currentMillis + 2 * 60 * 60 * 1000

      dateTimeUtility.nowMillis returns greaterThanSessionMillis

      val activeSessions: List[SessionInfo] = await(sessionsRepository.activeSessions(Some("test@example.com")))

      activeSessions must contain(List(sessionInfo).head)
    }

    "get immediate previous expired sessions" in new TestScope {
      val sessionId: BSONObjectID = BSONObjectID.generate
      val sessionInfo = SessionInfo("testId2", "test@example.com", BSONDateTime(currentMillis), "session2", "feedbackFormId", "topic2",
        1, meetup = true, "", 0.00, cancelled = false, active = true, BSONDateTime(currentMillis + 23 * 60 * 60 * 1000), Some("youtubeURL"), Some("slideShareURL"), 0, reminder = false, notification = false, sessionId)

      val created: Boolean = await(sessionsRepository.insert(sessionInfo).map(_.ok))
      created must beEqualTo(true)

      val greaterThanSessionExpirationMillis: Long = currentMillis + 24 * 60 * 60 * 1000

      dateTimeUtility.nowMillis returns greaterThanSessionExpirationMillis

      val expiredSessions: List[SessionInfo] = await(sessionsRepository.immediatePreviousExpiredSessions)

      expiredSessions must beEqualTo(List(sessionInfo))
    }

    "update rating for a given session ID" in new TestScope {
      val sessionId: BSONObjectID = BSONObjectID.generate
      val sessionInfo = SessionInfo("testId2", "test@example.com", BSONDateTime(currentMillis), "session2", "feedbackFormId", "topic2",
        1, meetup = true, "", 0.00, cancelled = false, active = true, BSONDateTime(currentMillis + 23 * 60 * 60 * 1000), Some("youtubeURL"), Some("slideShareURL"), 0, reminder = false, notification = false, sessionId)

      val created: Boolean = await(sessionsRepository.insert(sessionInfo).map(_.ok))
      created must beEqualTo(true)

      val result: UpdateWriteResult = await(sessionsRepository.updateRating(sessionId.stringify, 90.00))

      result.ok must beEqualTo(true)
    }

    "return ok as false for an invalid session" in new TestScope {
      val sessionId: BSONObjectID = BSONObjectID.generate

      val result: UpdateWriteResult = await(sessionsRepository.updateRating(sessionId.stringify, 90.00))

      result.ok must beEqualTo(false)
    }

  }

}

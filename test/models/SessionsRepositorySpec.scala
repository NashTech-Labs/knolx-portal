package models

import java.util.Date

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
  private val nowMillis = System.currentTimeMillis
  private val date = new Date(nowMillis)

  trait TestScope extends Scope {
    val dateTimeUtility = mock[DateTimeUtility]

    val sessionsRepository: SessionsRepository = new SessionsRepository(TestDb.reactiveMongoApi, mock[DateTimeUtility])
  }

  "Session repository" should {

    "insert session" in new TestScope {
      val userInfo = SessionInfo("testid", "test@example.com", BSONDateTime(date.getTime), "session", "feedbackFormId", "sessionRepoTest",
        1, meetup = true, "", cancelled = false, active = true, BSONDateTime(date.getTime), sessionId)

      val created = await(sessionsRepository.insert(userInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get sessions" in new TestScope {
      val sessions = await(sessionsRepository.sessions)
      sessions.headOption.map(_.email) contains "test@example.com"
      sessions.headOption.map(_.date.value) contains date.getTime
      sessions.headOption.exists(_.active)
    }

    "get session by id" in new TestScope {
      val session = await(sessionsRepository.getById(sessionId.stringify))

      session.map(_.email) contains "test@example.com"
      session.map(_.date.value) contains date.getTime
      session.exists(_.active)
    }

    "get sessions scheduled today" in new TestScope {
      val sessions = await(sessionsRepository.sessionsScheduledToday)

      sessions.headOption.map(_.email) contains "test@example.com"
      sessions.headOption.map(_.date.value) contains date.getTime
      sessions.headOption.exists(_.active)
    }

    "update session" in new TestScope {

      val updatedSession = UpdateSessionInfo(UpdateSessionInformation(sessionId.stringify, date,
        "testsession", "feedbackFormId", "updaterecord", 1), BSONDateTime(date.getTime))
      val updated = await(sessionsRepository.update(updatedSession).map(_.ok))

      updated must beEqualTo(true)
    }


    "paginate" in new TestScope {
      val page = await(sessionsRepository.paginate(1))

      page.size must beEqualTo(1)
    }

    "active count" in new TestScope {
      val count = await(sessionsRepository.activeCount)

      count must beEqualTo(1)
    }

    "delete session" in new TestScope {
      val deletedSessionUsers = await(sessionsRepository.delete(sessionId.stringify))

      val deletedSessionUser = deletedSessionUsers.getOrElse(JsObject(Seq.empty))

      (deletedSessionUser \ "active").getOrElse(JsBoolean(true)) must beEqualTo(JsBoolean(false))
    }

    "fetch sessions scheduled  till now" in new TestScope {
      val userInfo = SessionInfo("testid", "test@example.com", BSONDateTime(date.getTime), "session", "feedbackFormId", "sessionRepoTest",
        1, meetup = true, "", cancelled = false, active = true, BSONDateTime(date.getTime), sessionId)

      val sessions = await(sessionsRepository.getSessionsTillNow)

      sessions contains userInfo
    }

  }

}

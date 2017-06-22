package models

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.Scheduler
import akka.testkit.TestActorRef
import controllers.UpdateSessionInformation
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{JsBoolean, JsObject}
import play.api.libs.mailer.MailerClient
import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import schedulers.FeedbackFormsScheduler
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global

class SessionsRepositorySpec extends PlaySpecification with Mockito {

  val _id = BSONObjectID.generate
  val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-19")
  val dateDefault = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-19")

  trait TestScope extends Scope {
    val dateTimeUtility = mock[DateTimeUtility]

    val sessionsRepository: SessionsRepository = new SessionsRepository(TestDb.reactiveMongoApi, mock[DateTimeUtility])
  }

  "Session repository" should {

    "insert session" in new TestScope {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("2017-06-19")
      val userInfo = SessionInfo("testid", "test@example.com", BSONDateTime(date.getTime), "session", "feedbackFormId", "sessionRepoTest",
        meetup = true, "", cancelled = false, active = true, _id)

      val created = await(sessionsRepository.insert(userInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get sessions" in new TestScope {
      val sessions = await(sessionsRepository.sessions)

      val head = sessions.head

      head.email must beEqualTo("test@example.com")
      head.date must beEqualTo(BSONDateTime(date.getTime))
      head.active must beEqualTo(true)
    }

    "get session by id" in new TestScope {
      val session = await(sessionsRepository.getById(_id.stringify))

      val head = session.get

      head.email must beEqualTo("test@example.com")
      head.date must beEqualTo(BSONDateTime(date.getTime))
      head.active must beEqualTo(true)
    }

    "update session" in new TestScope {
      val updatedSession = UpdateSessionInformation(_id.stringify, date, "testsession", "updaterecord")

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
      val deletedSessionUsers = await(sessionsRepository.delete(_id.stringify))

      val deletedSessionUser = deletedSessionUsers.getOrElse(JsObject(Seq.empty))

      (deletedSessionUser \ "active").getOrElse(JsBoolean(true)) must beEqualTo(JsBoolean(false))
    }

  }

}

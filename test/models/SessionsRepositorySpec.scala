package models

import java.text.SimpleDateFormat
import java.util.Date

import controllers.UpdateSessionInformation
import play.api.libs.json.{JsBoolean, JsObject}
import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

class SessionsRepositorySpec extends PlaySpecification {

  val sessionsRepository = new SessionsRepository(TestDb.reactiveMongoApi)
  val _id: BSONObjectID = BSONObjectID.generate
  val date: Date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  val dateDefault: Date = new SimpleDateFormat("yyyy-MM-dd").parse("1111-11-11")
  val defaultSession = SessionInfo("", "", dateDefault, "", "", meetup = false, "", cancelled = false, active = false)

  "Session repository" should {

    "insert session" in {
      val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
      val userInfo = SessionInfo("testid", "test@example.com", date, "session", "sessionRepoTest", meetup = true, "", cancelled = false, active = true, _id)

      val created = await(sessionsRepository.insert(userInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get sessions" in {
      val sessions = await(sessionsRepository.sessions)

      val head = sessions.headOption.getOrElse(defaultSession)

      head.email must beEqualTo("test@example.com")
      head.date must beEqualTo(new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15"))
      head.active must beEqualTo(true)
    }

    "get session by id" in {
      val session = await(sessionsRepository.getById(_id.stringify))

      val head = session.getOrElse(defaultSession)

      head.email must beEqualTo("test@example.com")
      head.date must beEqualTo(new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15"))
      head.active must beEqualTo(true)
    }

    "update session" in {
      val updatedSession = UpdateSessionInformation(_id.stringify, date, "testsession", "updaterecord")

      val updated = await(sessionsRepository.update(updatedSession).map(_.ok))

      updated must beEqualTo(true)
    }

    "paginate" in {
      val page= await(sessionsRepository.paginate(1))
      page.size must beEqualTo(1)
    }

    "active count" in {
      val count = await(sessionsRepository.activeCount)
      count must beEqualTo(1)
    }

    "delete session" in {
      val deletedSessionUsers = await(sessionsRepository.delete(_id.stringify))

      val deletedSessionUser = deletedSessionUsers.getOrElse(JsObject(Seq.empty))

      (deletedSessionUser \ "active").getOrElse(JsBoolean(true)) must beEqualTo(JsBoolean(false))
    }

  }

}

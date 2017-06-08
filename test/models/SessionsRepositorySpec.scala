package models

import controllers.SessionFields.{Date, _}
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString}
import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import scala.concurrent.ExecutionContext.Implicits.global

class SessionsRepositorySpec extends PlaySpecification {

  val sessionsRepository = new SessionsRepository(TestDb.reactiveMongoApi)

  "Session repository" should {

    "create Session" in {
      val document =  BSONDocument(
        UserId -> "userId",
        Email -> "test@example.com",
        Date -> BSONDateTime(1496922356361L),
        Session -> "session",
        Topic -> "sessionRepoTest",
        Meetup -> true,
        Rating -> "",
        Cancelled -> false,
        Active -> true)
      val created = await(sessionsRepository.create(document).map(_.ok))
      created must beEqualTo(true)
    }

    "get sessions" in {
      val sessions = await(sessionsRepository.sessions)
      val head = sessions.headOption.getOrElse(JsObject(Seq.empty))
      (head \ Email).getOrElse(JsString("")) must beEqualTo(JsString("test@example.com"))
      (head \ Date).getOrElse(JsNumber(0)) must beEqualTo(JsObject(Map("$date" -> JsNumber(1496922356361L))))
      (head \ Active).getOrElse(JsBoolean(false)) must beEqualTo(JsBoolean(true))
    }

    "delete session" in {
      val sessions = await(sessionsRepository.sessions)
      val head = sessions.headOption.getOrElse(JsObject(Seq.empty))
      val jsValueId= (head \"_id"\"$oid").getOrElse(JsObject(Map("$oid"->JsString(""))))
      val id = jsValueId.asOpt[String].getOrElse("")
      val deletedSessionUsers= await(sessionsRepository.delete(id))
      val deletedSessionUser = deletedSessionUsers.headOption.getOrElse(JsObject(Seq.empty))
      (deletedSessionUser \ Active).getOrElse(JsBoolean(true)) must beEqualTo(JsBoolean(false))
    }

  }

}

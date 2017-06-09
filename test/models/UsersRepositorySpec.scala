package models

import controllers.SessionFields._
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString}
import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONDocument}

import scala.concurrent.ExecutionContext.Implicits.global

class UsersRepositorySpec extends PlaySpecification {

  val usersRepository = new UsersRepository(TestDb.reactiveMongoApi)

  "Users Repository" should {

    "create" in {
      val document =  BSONDocument(
        UserId -> "userId",
        Email -> "test@example.com",
        Date -> BSONDateTime(1496922356361L),
        Session -> "session",
        Topic -> "userRepoTest",
        Meetup -> true,
        Rating -> "",
        Cancelled -> false,
        Active -> true)
      val created = await(usersRepository.create(document).map(_.ok))
      created must beEqualTo(true)
    }

    "getByEmail" in {
      val user = await(usersRepository.getByEmail("test@example.com"))
      val head = user.headOption.getOrElse(JsObject(Seq.empty))
      (head \ Topic).getOrElse(JsString("")) must beEqualTo(JsString("userRepoTest"))
      (head \ Date).getOrElse(JsNumber(0)) must beEqualTo(JsObject(Map("$date" -> JsNumber(1496922356361L))))
      (head \ Active).getOrElse(JsBoolean(false)) must beEqualTo(JsBoolean(true))
    }

  }


}

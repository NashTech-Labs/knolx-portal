package models

import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext.Implicits.global

class SessionsRepositorySpec extends PlaySpecification {

  val sessionsRepository = new SessionsRepository(TestDb.reactiveMongoApi)

  "Session repository" should {

    "get sessions" in {
      val sessions = await(sessionsRepository.sessions)

      sessions must beEqualTo(List())
    }

  }

}

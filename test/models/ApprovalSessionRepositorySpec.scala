package models

import play.api.test.PlaySpecification

class ApprovalSessionRepositorySpec extends PlaySpecification {

  val approveSessionRepository = new ApprovalSessionsRepository(TestDb.reactiveMongoApi)

  val approveSessionInfo = ApproveSessionInfo("email",)

}

package models

import java.text.SimpleDateFormat

import controllers.UpdateApproveSessionInfo
import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONObjectID}

import scala.concurrent.ExecutionContext.Implicits.global

class ApprovalSessionRepositorySpec extends PlaySpecification {


  val approveSessionRepository = new ApprovalSessionsRepository(TestDb.reactiveMongoApi)

  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  private val currentDateString = "2017-07-12T14:30:00"
  private val currentDate = formatter.parse(currentDateString)
  private val currentMillis = currentDate.getTime
  private val _id: BSONObjectID = BSONObjectID.generate()

  val approveSessionInfo = UpdateApproveSessionInfo("email",BSONDateTime(currentMillis + 24*60*60*1000), "category",
    "subCategory", "topic", false, _id.stringify, false, false)

  "Approval Session Repository" should {

    "insert sesssion for approve by admin/superUser" in {
      val insert  = await(approveSessionRepository.insertSessionForApprove(approveSessionInfo).map(_.ok))

      insert must beEqualTo(true)
    }

    "get session with specified id" in {
      val sessions = await(approveSessionRepository.getSession(_id.stringify))

      sessions.email must beEqualTo("email")
    }

    "get all session created by user" in {
      val sessions = await(approveSessionRepository.getAllSession)

      sessions.head.email must beEqualTo("email")
    }

    "get all approved session" in {
      val approveSessionInfoByAdmin = UpdateApproveSessionInfo("approvedemail",BSONDateTime(currentMillis + 24*60*60*1000), "category",
        "subCategory", "topic", false, BSONObjectID.generate().stringify, true, false)

      val insert  = await(approveSessionRepository.insertSessionForApprove(approveSessionInfoByAdmin))
      val sessions = await(approveSessionRepository.getAllSession)

      sessions.reverse.head.email must beEqualTo("approvedemail")
    }

    "approve session created by user" in {
      val approve = await(approveSessionRepository.approveSession(_id.stringify))

      approve.ok must beEqualTo(true)
    }

    "decline session created by user" in {
      val sessionId = BSONObjectID.generate().stringify
      val declineSessionInfoByAdmin = UpdateApproveSessionInfo("declineemail",BSONDateTime(currentMillis + 24*60*60*1000), "category",
        "subCategory", "topic", false, sessionId, false, true)

      val insert  = await(approveSessionRepository.insertSessionForApprove(declineSessionInfoByAdmin))
      val decline = await(approveSessionRepository.declineSession(sessionId))

      decline.ok must beEqualTo(true)
    }
  }

}

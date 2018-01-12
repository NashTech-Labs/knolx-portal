package models

import java.text.SimpleDateFormat

import controllers.UpdateApproveSessionInfo
import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONObjectID}

import scala.concurrent.ExecutionContext.Implicits.global

class SessionRequestRepositorySpec extends PlaySpecification {

  val sessionRequestRepository = new SessionRequestRepository(TestDb.reactiveMongoApi)

  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  private val currentDateString = "2017-07-12T14:30:00"
  private val currentDate = formatter.parse(currentDateString)
  private val date = BSONDateTime(currentDate.getTime)
  private val _id: BSONObjectID = BSONObjectID.generate()

  val approveSessionInfo = UpdateApproveSessionInfo(date, _id.stringify, "topic", "email",
    "category", "subCategory")

  "Session Request Repository" should {

    "insert session for approval by admin/superUser" in {
      val insert = await(sessionRequestRepository.insertSessionForApprove(approveSessionInfo).map(_.ok))

      insert must beEqualTo(true)
    }

    "insert session for approval by admin/superUser when sessionId is not specified" in {
      val approveSessionInfoWithoutSessionId = UpdateApproveSessionInfo(date, freeSlot = true)

      val insert = await(sessionRequestRepository.insertSessionForApprove(approveSessionInfoWithoutSessionId).map(_.ok))

      insert must beEqualTo(true)
    }

    "get session with specified id" in {
      val sessions = await(sessionRequestRepository.getSession(_id.stringify))

      sessions.get.email must beEqualTo("email")
    }

    "get all sessions to display in calendar" in {
      val insertApproveSessionInfo = UpdateApproveSessionInfo(BSONDateTime(1514745000010L), _id.stringify, "topic", "email",
        "category", "subCategory")

      await(sessionRequestRepository.insertSessionForApprove(insertApproveSessionInfo))

      val sessions = await(sessionRequestRepository.getSessionsInMonth(1514745000000L, 1517423399999L))

      sessions.head.email must beEqualTo("email")
    }

    "get starting 10 booked sessions" in {
      val result = await(sessionRequestRepository.paginate(1))

      result.head.email must beEqualTo("email")
    }

    "get starting 10 booked sessions where email is email" in {
      val result = await(sessionRequestRepository.paginate(1, Some("email")))

      result.head.email must beEqualTo("email")
    }

    "get count of all booked sessions" in {
      val result = await(sessionRequestRepository.activeCount())

      result must beEqualTo(1)
    }

    "get count of booked sessions where email matches the given keyword" in {
      val result = await(sessionRequestRepository.activeCount(Some("email")))

      result must beEqualTo(1)
    }

    "get all approved sessions" in {
      val approveSessionInfoByAdmin = UpdateApproveSessionInfo(date, _id.stringify,
        "topic", "approvedemail", "category", "subCategory", approved = true)

      val insert = await(sessionRequestRepository.insertSessionForApprove(approveSessionInfoByAdmin))
      val sessions = await(sessionRequestRepository.getAllApprovedSession)

      sessions.head.email must beEqualTo("approvedemail")
    }

    "decline session created by user" in {
      val sessionId = BSONObjectID.generate().stringify
      val declineSessionInfoByAdmin = UpdateApproveSessionInfo(date, _id.stringify,
        "topic", "email", "category", "subCategory", decline = true)

      val insert = await(sessionRequestRepository.insertSessionForApprove(declineSessionInfoByAdmin))
      val decline = await(sessionRequestRepository.declineSession(sessionId))

      decline.ok must beEqualTo(true)
    }

    "update date for pending session" in {
      val sessionId = BSONObjectID.generate().stringify
      val declineSessionInfoByAdmin = UpdateApproveSessionInfo(date, _id.stringify,
        "topic", "email", "category", "subCategory", decline = true)

      val insert = await(sessionRequestRepository.insertSessionForApprove(declineSessionInfoByAdmin))
      val update = await(sessionRequestRepository.updateDateForPendingSession(sessionId, date))
      update.ok must beEqualTo(true)
    }

    "get all pending sessions" in {
      await(sessionRequestRepository.insertSessionForApprove(approveSessionInfo))

      val session = await(sessionRequestRepository.getAllPendingSession)

      session.head.email must be equalTo "email"
    }

    "delete free slot with specified id" in {
      val freeSlotId = BSONObjectID.generate()
      val freeSlot = UpdateApproveSessionInfo(date, freeSlotId.stringify, freeSlot = true)
      await(sessionRequestRepository.insertSessionForApprove(freeSlot))

      val session = await(sessionRequestRepository.deleteFreeSlot(freeSlotId.stringify))

      session.ok must be equalTo true
    }

    "get all free slots" in {
      val session = await(sessionRequestRepository.getAllFreeSlots)

      session.head.freeSlot must be equalTo true
    }

  }

}

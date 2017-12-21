package controllers

import java.text.SimpleDateFormat
import java.time.{LocalDate, LocalDateTime}

import helpers.TestEnvironment
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.{Application, Logger}
import play.api.mvc.Results
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.test.CSRFTokenHelper._
import reactivemongo.api.commands.{DefaultWriteResult, UpdateWriteResult}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RecommendationControllerSpec extends PlaySpecification with Results {

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val email = "test@knoldus.com"
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val localDate = LocalDateTime.now()

  private val emailObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))

  private val recommendations = Future.successful(List(RecommendationInfo(Some("test@knoldus.com"),
  "recommendation",
    BSONDateTime(date.getTime),
    BSONDateTime(date.getTime))))

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    val recommendationsRepository = mock[RecommendationsRepository]
    val recommendationsResponseRepository = mock[RecommendationResponseRepository]
    val dateTimeUtility = mock[DateTimeUtility]

    lazy val controller =
      new RecommendationController(
        knolxControllerComponent.messagesApi,
        recommendationsRepository,
        knolxControllerComponent,
        dateTimeUtility,
        recommendationsResponseRepository)

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Recommendation Controller" should {

    "render recommendation page" in new WithTestApplication {
      val result = controller.renderRecommendationPage(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }

    "store recommendation when email exists" in new WithTestApplication {
      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation("Recommendation")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken
      )

      status(result) must be equalTo OK
    }

    "store recommendation when email doesn't exist" in new WithTestApplication {
      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation("Recommendation")(
        FakeRequest()
          .withSession("username" -> "")
          .withCSRFToken
      )

      status(result) must be equalTo OK
    }

    "not store recommendation" in new WithTestApplication {
      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation("Recommendation")(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "render recommendationList to admin/super user" in new WithTestApplication {
      val pageNumber = 1
      val filter = "all"

      recommendationsRepository.paginate(pageNumber, filter) returns recommendations
      recommendationsResponseRepository.getVote(any[String], any[String]) returns Future.successful("upvote")
      dateTimeUtility.toLocalDateTime(date.getTime) returns localDate

      val result = controller.recommendationList(pageNumber, filter)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=",
          "admin" -> "DqDK4jVae2aLvChuBPCgmfRWXKArji6AkjVhqSxpMFP6I6L/FkeK5HQz1dxzxzhP")
      )

      status(result) must be equalTo OK
    }

    "render recommendationList to logged in / non-logged in user" in new WithTestApplication {
      val pageNumber = 1
      val filter = "all"

      recommendationsRepository.paginate(pageNumber, filter) returns recommendations
      recommendationsResponseRepository.getVote(any[String], any[String]) returns Future.successful("upvote")

      val result = controller.recommendationList(pageNumber, filter)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "approve recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.approveRecommendation(recommendationId) returns writeResult

      val result = controller.approveRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not approve recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      recommendationsRepository.approveRecommendation(recommendationId) returns writeResult

      val result = controller.approveRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "decline recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.declineRecommendation(recommendationId) returns writeResult

      val result = controller.declineRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not decline recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      recommendationsRepository.declineRecommendation(recommendationId) returns writeResult

      val result = controller.declineRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "upvote the recommendation if user has first downvoted" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("downvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = true) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not upvote the recommendation if user has already upvoted" in new WithTestApplication {
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("upvote")

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "upvote the recommendation if user has not given any response yet" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "throw bad request while upvoting the recommendation" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val wrongUpdateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("downvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationResponseRepositoryInfo]) returns wrongUpdateWriteResult

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "downvote the recommendation if user has first upvoted" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("upvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = true) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not downvote the recommendation if user has already downvoted" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("downvote")

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "downvote the recommendation if user has not given any response yet" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "throw bad request while downvoting the recommendation" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val wrongUpdateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("upvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationResponseRepositoryInfo]) returns wrongUpdateWriteResult

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "mark recommendation as done" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.doneRecommendation(recommendationId) returns updateWriteResult

      val result = controller.doneRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not mark recommendation as done" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.doneRecommendation(recommendationId) returns updateWriteResult

      val result = controller.doneRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "mark recommendation as pending" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.pendingRecommendation(recommendationId) returns updateWriteResult

      val result = controller.pendingRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not mark recommendation as pending" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.pendingRecommendation(recommendationId) returns updateWriteResult

      val result = controller.pendingRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }
  }

}

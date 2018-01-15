package models

import java.text.SimpleDateFormat
import org.specs2.mock.Mockito
import play.api.test.PlaySpecification
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility
import scala.concurrent.ExecutionContext.Implicits.global

class RecommendationsRepositorySpec extends PlaySpecification with Mockito {

  private val recommendationId = BSONObjectID.generate
  private val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  private val submissionDateString = "2017-07-12T14:30:00"
  private val updateDateString = "2017-07-10T14:30:00"
  private val submissionDate = formatter.parse(submissionDateString).getTime
  private val updateDate = formatter.parse(updateDateString).getTime

  val dateTimeUtility: DateTimeUtility = new DateTimeUtility()
  val recommendationRepository = new RecommendationsRepository(TestDb.reactiveMongoApi, dateTimeUtility)

  val recommendationInfo = RecommendationInfo(Some("email"), "name", "topic", "recommendation", BSONDateTime(submissionDate),
    BSONDateTime(updateDate), approved = true, decline = false, pending = false, done = true, book = false, upVotes = 10,
    downVotes = 15, recommendationId)

  "Recommendations Repository" should {

    "insert recommendation" in {
      val inserted = await(recommendationRepository.insert(recommendationInfo).map(_.ok))

      inserted must beEqualTo(true)
    }

    "get all recommendation according to submission date" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "get all recommendation according to update date" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1, viewBy = "recent"))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "approved recommendation" in {

      val approve = await(recommendationRepository.approveRecommendation(recommendationId.stringify))

      approve.ok must beEqualTo(true)
    }

    "get approved recommendation" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1, "approved"))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "get recommendation by Id" in {
      val maybeRecommendation = await(recommendationRepository.getRecommendationById(recommendationId.stringify))

      maybeRecommendation.get.name must beEqualTo("name")
    }

    "update book recommendation value" in {
      val done = await(recommendationRepository.bookRecommendation(recommendationId.stringify))

      done.ok must beEqualTo(true)
    }

    "get booked recommendation" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1, "book"))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "decline recommendation" in {

      val decline = await(recommendationRepository.declineRecommendation(recommendationId.stringify))

      decline.ok must beEqualTo(true)
    }

    "get decline recommendation" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1, "decline"))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "update pending recommendation value" in {
      val pending = await(recommendationRepository.pendingRecommendation(recommendationId.stringify))

      pending.ok must beEqualTo(true)
    }

    "get pending recommendation" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1, "pending"))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "update done recommendation value" in {
      val done = await(recommendationRepository.doneRecommendation(recommendationId.stringify))

      done.ok must beEqualTo(true)
    }

    "get done recommendation" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1, "done"))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "get recommendation for unmatched string" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1, "unmatched"))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "upvote a recommendation when already voted" in {
      val upVote = await(recommendationRepository.upVote(recommendationId.stringify, alreadyVoted = true))

      upVote.ok must beEqualTo(true)
    }

    "upvote a recommendation user did not voted earlier" in {
      val upVote = await(recommendationRepository.upVote(recommendationId.stringify, alreadyVoted = false))

      upVote.ok must beEqualTo(true)
    }

    "downvote a recommendation when already voted" in {
      val downVote = await(recommendationRepository.downVote(recommendationId.stringify, alreadyVoted = true))

      downVote.ok must beEqualTo(true)
    }

    "downvote a recommendation user did not voted earlier" in {
      val upVote = await(recommendationRepository.downVote(recommendationId.stringify, alreadyVoted = false))

      upVote.ok must beEqualTo(true)
    }

    "cancel booked recommendation" in {
      val unbook = await(recommendationRepository.cancelBookedRecommendation(recommendationId.stringify))

      unbook.ok must beEqualTo(true)
    }

    "return all sessions waiting for admin's action" in {
      val pendingRecommendationInfo = RecommendationInfo(Some("email"), "name", "topic", "recommendation", BSONDateTime(submissionDate),
        BSONDateTime(updateDate), approved = false, decline = false, pending = false, done = true, book = false, upVotes = 10,
        downVotes = 15, BSONObjectID.generate)

      await(recommendationRepository.insert(pendingRecommendationInfo))

      val pendingRecommendations = await(recommendationRepository.allPendingRecommendations)

      pendingRecommendations must beEqualTo(1)
    }
  }

}

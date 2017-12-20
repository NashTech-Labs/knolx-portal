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

  val recommendationInfo = RecommendationInfo(Some("email"), "recommendation", BSONDateTime(submissionDate),
    BSONDateTime(updateDate), approved = true, decline = false, pending = true, done = false, upVotes = 10,
    downVotes = 15, recommendationId)

  "Recommendations Respository" should {

    "insert recommendation" in {
      val inserted = await(recommendationRepository.insert(recommendationInfo).map(_.ok))

      inserted must beEqualTo(true)
    }

    "approved recommendation" in {

      val approve = await(recommendationRepository.approveRecommendation(recommendationId.stringify))

      approve.ok must beEqualTo(true)
    }

    "decline recommendation" in {

      val decline = await(recommendationRepository.approveRecommendation(recommendationId.stringify))

      decline.ok must beEqualTo(true)
    }

    "get paginated recommendation" in {
      val paginatedRecommendation = await(recommendationRepository.paginate(1))

      paginatedRecommendation.head.recommendation must beEqualTo("recommendation")
    }

    "upvote a recommendation" in {
      val upVote = await(recommendationRepository.upVote(recommendationId.stringify, true))

      upVote.ok must beEqualTo(true)
    }

    "downvote a recommendation" in {
      val downVote = await(recommendationRepository.downVote(recommendationId.stringify, true))

      downVote.ok must beEqualTo(true)
    }

    "update pending recommendation value" in {
      val pending = await(recommendationRepository.pendingRecommendation(recommendationId.stringify))

      pending.ok must beEqualTo(true)
    }

    "update done recommendation value" in {
      val done = await(recommendationRepository.doneRecommendation(recommendationId.stringify))

      done.ok must beEqualTo(true)
    }
  }

}

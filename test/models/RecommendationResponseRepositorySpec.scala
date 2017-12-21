package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

class RecommendationResponseRepositorySpec extends PlaySpecification {

  val recommendationResponseId = BSONObjectID.generate()
  val recommendationId = BSONObjectID.generate()

  val recommendationResponseRepository = new RecommendationResponseRepository(TestDb.reactiveMongoApi)
  val recommendationResponseInfo = RecommendationResponseRepositoryInfo("email", recommendationId.stringify, true, false,
    recommendationResponseId)

  "Recommendation Response" should {

    "upsert a recommendation response for the user" in {

      val upserted = await(recommendationResponseRepository.upsert(recommendationResponseInfo))

      upserted.ok must beEqualTo(true)
    }

    "get vote for the user who upvoted for the particular recommendation" in {

      val getVote = await(recommendationResponseRepository.getVote("email", recommendationId.stringify))

      getVote must beEqualTo("upvote")
    }

    "get vote for the user who downvoted for the particular recommendation" in {

      val getVote = await(recommendationResponseRepository.getVote("email", recommendationId.stringify))

      getVote must beEqualTo("downvote")
    }

    "get vote for unmatched case" in {

      val getVote = await(recommendationResponseRepository.getVote("email", recommendationId.stringify))

      getVote must beEqualTo("")
    }

    "get vote for wrong user for the particular recommendation" in {

      val getVote = await(recommendationResponseRepository.getVote("", recommendationId.stringify))

      getVote must beEqualTo("upvote")
    }
  }

}

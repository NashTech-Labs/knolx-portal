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

    "get vote for the user who already voted for the particular recommendation" in {

      val getVote = await(recommendationResponseRepository.getVote("email", recommendationId.stringify))

      getVote must beEqualTo("upvote")
    }
  }

}

package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

class RecommendationResponseRepositorySpec extends PlaySpecification {

  val recommendationId = BSONObjectID.generate()

  val recommendationsResponseRepository = new RecommendationsResponseRepository(TestDb.reactiveMongoApi)
  val recommendationResponseInfoForUpVote =
    RecommendationsResponseRepositoryInfo("emailUpVote", recommendationId.stringify, upVote = true, downVote = false,
      BSONObjectID.generate())
  val recommendationResponseInfoForDownVote =
    RecommendationsResponseRepositoryInfo("emailDownVote", recommendationId.stringify, upVote = false, downVote = true,
      BSONObjectID.generate())
  val recommendationResponseInfoForNoVote =
    RecommendationsResponseRepositoryInfo("emailNoVote", recommendationId.stringify, upVote = false, downVote = false,
      BSONObjectID.generate())

  "Recommendation Response" should {

    "upsert a recommendation response for the user" in {
      val upsertedUpVote = await(recommendationsResponseRepository.upsert(recommendationResponseInfoForUpVote))
      val upsertedDownVote = await(recommendationsResponseRepository.upsert(recommendationResponseInfoForDownVote))
      val upsertedNoVote = await(recommendationsResponseRepository.upsert(recommendationResponseInfoForNoVote))

      upsertedUpVote.ok must beEqualTo(true)
    }

    "get vote for the user who upvoted for the particular recommendation" in {
      val getVote = await(recommendationsResponseRepository.getVote("emailUpVote", recommendationId.stringify))

      getVote must beEqualTo("upvote")
    }

    "get vote for the user who downvoted for the particular recommendation" in {
      val getVote = await(recommendationsResponseRepository.getVote("emailDownVote", recommendationId.stringify))

      getVote must beEqualTo("downvote")
    }

    "get vote for unmatched case" in {
      val getVote = await(recommendationsResponseRepository.getVote("emailNoVote", recommendationId.stringify))

      getVote must beEqualTo("")
    }

    "get vote for wrong user for the particular recommendation" in {
      val getVote = await(recommendationsResponseRepository.getVote("", recommendationId.stringify))

      getVote must beEqualTo("")
    }
  }


}

package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

class RecommendationResponseRepositorySpec extends PlaySpecification {

  val recommendationId = BSONObjectID.generate()

  val recommendationResponseRepository = new RecommendationResponseRepository(TestDb.reactiveMongoApi)
  val recommendationResponseInfoForUpVote = RecommendationResponseRepositoryInfo("emailUpVote", recommendationId.stringify, true, false,
    BSONObjectID.generate())
  val recommendationResponseInfoForDownVote = RecommendationResponseRepositoryInfo("emailDownVote", recommendationId.stringify, false, true,
    BSONObjectID.generate())
  val recommendationResponseInfoForNoVote = RecommendationResponseRepositoryInfo("emailNoVote", recommendationId.stringify, false, false,
    BSONObjectID.generate())

  "Recommendation Response" should {

    "upsert a recommendation response for the user" in {

      val upsertedUpVote = await(recommendationResponseRepository.upsert(recommendationResponseInfoForUpVote))
      val upsertedDownVote = await(recommendationResponseRepository.upsert(recommendationResponseInfoForDownVote))
      val upsertedNoVote = await(recommendationResponseRepository.upsert(recommendationResponseInfoForNoVote))

      upsertedUpVote.ok must beEqualTo(true)
    }

    "get vote for the user who upvoted for the particular recommendation" in {

      val getVote = await(recommendationResponseRepository.getVote("emailUpVote", recommendationId.stringify))

      getVote must beEqualTo("upvote")
    }

    "get vote for the user who downvoted for the particular recommendation" in {

      val getVote = await(recommendationResponseRepository.getVote("emailDownVote", recommendationId.stringify))

      getVote must beEqualTo("downvote")
    }

    "get vote for unmatched case" in {

      val getVote = await(recommendationResponseRepository.getVote("emailNoVote", recommendationId.stringify))

      getVote must beEqualTo("")
    }

    "get vote for wrong user for the particular recommendation" in {

      val getVote = await(recommendationResponseRepository.getVote("", recommendationId.stringify))

      getVote must beEqualTo("")
    }
  }

}

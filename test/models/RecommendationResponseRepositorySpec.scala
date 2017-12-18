package models

import org.specs2.mock.Mockito
import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

class RecommendationResponseRepositorySpec extends PlaySpecification with Mockito {

  val recommendationResponseId = BSONObjectID.generate()
  val recommendationId = BSONObjectID.generate()

  val recommendationResponseRepository  = new RecommendationResponseRepository(TestDb.reactiveMongoApi)
  val recommendationResponseInfo = RecommendationResponseRepositoryInfo("email", recommendationId.stringify, true, false,
    recommendationResponseId)

  "Recommendation Response" should {

    "upsert a recommendation response for the user" in {

      val upserted = await(recommendationResponseRepository.upsert(recommendationResponseInfo).map(_.ok))

      upserted must beEqualTo(true)
    }
  }

}

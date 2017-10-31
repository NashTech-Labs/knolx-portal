package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

class TechnologiesRepositorySpec extends PlaySpecification {

  val technologiesRepository = new TechnologiesRepository(TestDb.reactiveMongoApi)

  private val categoryId = BSONObjectID.generate
  val categoryInfo = CategoryInfo("play framework", categoryId)

  "Technologies Repository" should {

    "insert a new category" in {
      val created: Boolean = await(technologiesRepository.insert(categoryInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get category list" in {
      val categories: List[CategoryInfo] = await(technologiesRepository.getCategories)

      categories must beEqualTo(List(categoryInfo))

    }
  }

}

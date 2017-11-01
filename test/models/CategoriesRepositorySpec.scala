package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

class CategoriesRepositorySpec extends PlaySpecification {

  val categoriesRepository = new CategoriesRepository(TestDb.reactiveMongoApi)

  private val categoryId = BSONObjectID.generate
  val categoryInfo = CategoryInfo("Front-End", List("Angular Js","D3JS"),categoryId)

  "Technologies Repository" should {

    "upsert a new category" in {
      val created: Boolean = await(categoriesRepository.upsert(categoryInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get category list" in {
      val categories: List[CategoryInfo] = await(categoriesRepository.getCategories)

      categories.head.subCategory.head must beEqualTo("Angular Js")

    }
  }

}

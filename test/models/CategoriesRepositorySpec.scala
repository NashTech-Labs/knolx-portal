package models

import play.api.test.PlaySpecification
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

class CategoriesRepositorySpec extends PlaySpecification {

  val categoriesRepository = new CategoriesRepository(TestDb.reactiveMongoApi)

  private val categoryId = BSONObjectID.generate
  val categoryInfo = CategoryInfo("Front-End", List("Angular Js","D3JS"),categoryId)

  "Categories Repository" should {

    "insert a new category" in {
      val created = await(categoriesRepository.insertCategory("Backend"))

      created.ok must beEqualTo(true)
    }

    "upsert a sub-category" in {
      val created: Boolean = await(categoriesRepository.upsert(categoryInfo).map(_.ok))

      created must beEqualTo(true)
    }

    "get category name by its Id" in {
      val categoryName = await(categoriesRepository.getCategoryNameById(categoryId.stringify))

      categoryName must beEqualTo(Some("Front-End"))
    }

    "get category list" in {
      val categoriesList: List[CategoryInfo] = await(categoriesRepository.getCategories)

      categoriesList.reverse.head.subCategory must beEqualTo (List("Angular Js","D3JS"))
    }

    "modify a primary category" in {
      val update = await(categoriesRepository.modifyPrimaryCategory(categoryId.stringify,"Front End"))

      update.ok must beEqualTo(true)
    }

    "modify a sub-category" in {
      val update = await(categoriesRepository.modifySubCategory(categoryId.stringify,"D3JS","D3 JS"))

      update.ok must beEqualTo(true)
    }

    "delete a primary category" in {
      val deletePrimaryCategory = await(categoriesRepository.deletePrimaryCategory(categoryId.stringify))

      deletePrimaryCategory.ok must beEqualTo(true)
    }

    "delete sub Category" in {
      val deleteSubCategory = await(categoriesRepository.deleteSubCategory(categoryId.stringify,"Angular Js"))

      deleteSubCategory.ok must beEqualTo(true)
    }

  }

}

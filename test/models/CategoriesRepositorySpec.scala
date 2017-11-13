package models

import com.sun.corba.se.spi.ior.ObjectId
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

    "get category list" in {
      val insertCategoryInfo = CategoryInfo("Backend",List("scala"),BSONObjectID.generate)
      val insert = await(categoriesRepository.insertCategory("Backend"))
      val subCategory = await(categoriesRepository.upsert(insertCategoryInfo))
      val categoriesList: List[CategoryInfo] = await(categoriesRepository.getCategories)

      categoriesList.head.subCategory must beEqualTo (List("scala"))
    }

    "modify a primary category" in {
      val update = await(categoriesRepository.modifyPrimaryCategory("Front-End","Front End"))

      update.ok must beEqualTo(true)
    }

    "modify a sub-category" in {
      val update = await(categoriesRepository.modifySubCategory("Front-End","D3JS","D3 JS"))

      update.ok must beEqualTo(true)
    }

    "delete a primary category" in {
      val deletePrimaryCateogry = await(categoriesRepository.deletePrimaryCategory("Front-End"))

      deletePrimaryCateogry.ok must beEqualTo(true)
    }

    "delete sub Category" in {
      val deleteSubCategory = await(categoriesRepository.deleteSubCategory("Front-End","Angular Js"))

      deleteSubCategory.ok must beEqualTo(true)
    }

  }

}

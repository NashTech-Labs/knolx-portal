package models

import javax.inject.Inject

import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.bson.{BSONDocument, BSONDocumentWriter, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import models.categoriesJsonFormats._
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class CategoryInfo(categoryName: String,
                        subCategory: List[String],
                        _id: BSONObjectID = BSONObjectID.generate
                       )

object categoriesJsonFormats {

  import play.api.libs.json.Json

  implicit val categoriesFormat = Json.format[CategoryInfo]

}

class CategoriesRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("categories"))

  def insertCategory(categoryName: String)(implicit ex: ExecutionContext): Future[WriteResult] ={
    val categoryInfo = CategoryInfo(categoryName, List())
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(categoryInfo))
  }

  def upsert(category: CategoryInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("categoryName" -> category.categoryName)
    val modifier = BSONDocument("$addToSet" -> BSONDocument(
      "subCategory" -> BSONDocument(
        "$each" -> category.subCategory)))
    collection.flatMap(_.update(selector, modifier))

  }

  def getCategories(implicit ex: ExecutionContext): Future[List[CategoryInfo]] = {
    Logger.info("getCategories")
    collection.
      flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj()).
          cursor[CategoryInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[CategoryInfo]]()))
  }

  def modifyPrimaryCategory(oldCategoryName: String, newCategoryName: String)(implicit ex : ExecutionContext): Future[UpdateWriteResult] = {

    val selector = BSONDocument("categoryName" -> oldCategoryName)
    val modifier = BSONDocument("$set" -> BSONDocument(
      "categoryName" -> newCategoryName
    ))
    collection.flatMap(_.update(selector,modifier))
  }

  def modifySubCategory(categoryName: String, oldSubCategoryName: String,
                        newSubCategoryName: String)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {

    Logger.info("Inside models modifySubCategory")
    val selector = BSONDocument("categoryName" -> categoryName,"subCategory" -> oldSubCategoryName)
    val modifier = BSONDocument("$set" -> BSONDocument(
      "subCategory.$" -> newSubCategoryName
    ))
    collection.flatMap(_.update(selector,modifier))
  }

  def deletePrimaryCategory(categoryName: String)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("categoryName" -> categoryName)

    collection.flatMap(_.remove(selector))
  }

  def deleteSubCategory(categoryName: String, subCategory: String)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {
    Logger.info("Delete sub category")
    val selector = BSONDocument("categoryName" -> categoryName)
    val modifier = BSONDocument("$pull" -> BSONDocument(
      "subCategory"-> subCategory))
    Logger.info("At end delete sub category")
    collection.flatMap(_.update(selector, modifier, multi = true))

  }
}
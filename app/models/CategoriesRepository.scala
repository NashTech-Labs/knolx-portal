package models

import javax.inject.Inject

import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import models.CategoriesJsonFormats._

import play.api.libs.json.{JsValue, Json}
import models.CategoriesJsonFormats._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class CategoryInfo(categoryName: String, subCategory: List[String], _id: BSONObjectID = BSONObjectID.generate)

object CategoriesJsonFormats {
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
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> category._id.stringify))
    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "categoryName" -> category.categoryName),
        "$addToSet" -> BSONDocument(
          "subCategory" -> BSONDocument(
        "$each" -> category.subCategory)))

    collection.flatMap(_.update(selector, modifier, upsert = true))

  }

  def getCategories(implicit ex: ExecutionContext): Future[List[CategoryInfo]] = {
    collection.
      flatMap(jsonCollection =>
        jsonCollection.
          find(Json.obj()).
          cursor[CategoryInfo](ReadPreference.Primary)
          .collect[List](-1, FailOnError[List[CategoryInfo]]()))
  }

  def modifyPrimaryCategory(categoryId: String, newCategoryName: String)(implicit ex : ExecutionContext): Future[UpdateWriteResult] = {

    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> categoryId))
    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "categoryName" -> newCategoryName))

    collection.flatMap(_.update(selector,modifier))
  }

  def modifySubCategory(categoryId: String,
                        oldSubCategoryName: String,
                        newSubCategoryName: String)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {

    val selector = BSONDocument(
      "_id" ->  BSONDocument(
        "$oid" -> categoryId),
      "subCategory" -> oldSubCategoryName)

    val modifier =
      BSONDocument(
        "$set" -> BSONDocument(
          "subCategory.$" -> newSubCategoryName))
    collection.flatMap(_.update(selector,modifier))
  }

  def deletePrimaryCategory(categoryId: String)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> categoryId))
    collection.flatMap(_.remove(selector))
  }

  def deleteSubCategory(categoryId: String, subCategory: String)(implicit ex: ExecutionContext): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> BSONDocument("$oid" -> categoryId))
    val modifier =
      BSONDocument(
        "$pull" -> BSONDocument(
          "subCategory"-> subCategory))
    collection.flatMap(_.update(selector, modifier, multi = true))
  }

  def getCategoryNameById(categoryId: String): Future[Option[String]] = {
    val condition = BSONDocument("_id" -> BSONDocument("$oid" -> categoryId))
    val projection = BSONDocument("_id" -> 0, "categoryName" -> 1)
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(condition, projection)
          .cursor[JsValue](ReadPreference.primary)
          .collect[List](-1, FailOnError[List[JsValue]]())
      ).map(_.flatMap(_ ("categoryName").asOpt[String]).headOption)
  }

}

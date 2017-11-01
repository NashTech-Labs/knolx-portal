package models

import javax.inject.Inject

import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONDocumentWriter, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import models.categoriesJsonFormats._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat


/*
case class SubCategory(subCategory: List[String])
*/

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

  def upsert(category: CategoryInfo)(implicit ex: ExecutionContext): Future[WriteResult] = {
    val selector = BSONDocument("categoryName" -> category.categoryName)
    val modifier = BSONDocument("categoryName" -> category.categoryName,
                                "subCategory" -> category.subCategory)

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
}

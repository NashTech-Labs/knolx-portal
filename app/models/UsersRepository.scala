package models

import javax.inject.Inject
import models.UserJsonFormats._
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.collection.JSONCollection
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class UserInfo (email: String, password: String, algorithm: String, active: Boolean, admin: Boolean)

object UserJsonFormats {
  import play.api.libs.json.Json
  implicit val feedFormat = Json.format[UserInfo]
}

class UsersRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("users"))

  def getByEmail(email: String)(implicit ex: ExecutionContext): Future[List[JsObject]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("email" -> email.toLowerCase))
          .cursor[JsObject](ReadPreference.Primary)
          .collect[List]())
  }

  def insert(user: UserInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(user))

}

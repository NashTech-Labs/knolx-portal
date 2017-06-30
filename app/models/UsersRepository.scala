package models

import javax.inject.Inject

import models.UserJsonFormats._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class UserInfo(email: String,
                    password: String,
                    algorithm: String,
                    active: Boolean,
                    admin: Boolean,
                    _id: BSONObjectID = BSONObjectID.generate)

object UserJsonFormats {

  import play.api.libs.json.Json

  implicit val userFormat = Json.format[UserInfo]
}

class UsersRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) {

  import play.modules.reactivemongo.json._

  protected def collection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection[JSONCollection]("users"))

  def getByEmail(email: String)(implicit ex: ExecutionContext): Future[List[UserInfo]] = {
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .find(Json.obj("email" -> email.toLowerCase))
          .cursor[UserInfo](ReadPreference.Primary)
          .collect[List](-1, reactivemongo.api.Cursor.FailOnError[List[UserInfo]]()))
  }

  def insert(user: UserInfo)(implicit ex: ExecutionContext): Future[WriteResult] =
    collection
      .flatMap(jsonCollection =>
        jsonCollection
          .insert(user))

}

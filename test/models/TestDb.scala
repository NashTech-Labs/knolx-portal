package models

import com.github.simplyscala.MongoEmbedDatabase
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoApi

import scala.concurrent.Await
import scala.concurrent.duration._

object TestDb extends MongoEmbedDatabase {

  mongoStart(27018)

  private val appBuilder = new GuiceApplicationBuilder().build

  val reactiveMongoApi = appBuilder.injector.instanceOf[ReactiveMongoApi]

  private val defaultDb = Await.result(reactiveMongoApi.database, Duration.Inf)

  require(defaultDb.connection.active)

}

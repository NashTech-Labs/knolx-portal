package models

import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import org.specs2.mock.Mockito
import org.specs2.specification.BeforeAfterAll
import play.api.test.PlaySpecification
import play.modules.reactivemongo.ReactiveMongoApi
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SessionsRepositorySpec extends PlaySpecification with BeforeAfterAll with MongoEmbedDatabase with Mockito
{
  var mongoInstance: MongodProps = _
  override def beforeAll(): Unit ={
    try{ mongoInstance = mongoStart(27017) }
    catch { case ex:Exception => ex.printStackTrace()}
  }


  "Session repository" should {
    "delete the id" in {
      testObject.sessionsRepository.delete("ankit") must be equalTo Future.successful(None)
    }
  }


  override def afterAll(): Unit ={
    mongoStop(mongoInstance)
  }

  def testObject: TestObject = {
    val mockedReactiveMongoApi: ReactiveMongoApi = mock[ReactiveMongoApi]
    val sessionsRepository: SessionsRepository = new SessionsRepository(mockedReactiveMongoApi)
    TestObject(mockedReactiveMongoApi, sessionsRepository)
  }

  case class TestObject(messagesApi: ReactiveMongoApi, sessionsRepository: SessionsRepository)

}
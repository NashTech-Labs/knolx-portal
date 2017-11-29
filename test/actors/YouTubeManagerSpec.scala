package actors

import java.io.FileInputStream

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import helpers.{DummyYouTubeDetailsActor, DummyYouTubeUploader, TestEnvironment}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.{Application, Configuration}
import play.api.inject.BindingKey
import play.api.libs.Files
import play.api.libs.concurrent.InjectedActorSupport
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}


class YouTubeManagerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with WordSpecLike with DefaultAwaitTimeout with FutureAwaits with MustMatchers
  with Mockito with ImplicitSender with BeforeAndAfterAll {

  private val sessionId = "SessionId"
  val configuration = mock[Configuration]

  def this() = this(ActorSystem("MySpec"))

  object DummyYouTubeUploaderFactory extends actors.ConfiguredYouTubeUploader.Factory {
    def apply(): Actor = {
      val youtubeUploaderActorRef = TestActorRef[DummyYouTubeUploader]
      youtubeUploaderActorRef.underlyingActor
    }
  }

  object DummyYouTubeDetailsActorFactory extends actors.ConfiguredYouTubeDetailsActor.Factory {
    def apply(): Actor = {
      val youtubeCategoryActorRef = TestActorRef[DummyYouTubeDetailsActor]
      youtubeCategoryActorRef.underlyingActor
    }
  }

  trait UnitTestScope extends Scope {

    sealed trait TestInjectedActorSupport extends InjectedActorSupport {
      override def injectedChild(create: => Actor, name: String, props: Props => Props = identity)(implicit context: ActorContext): ActorRef = {
        if (name.contains("YouTubeDetailsActor")) {
          TestActorRef[DummyYouTubeDetailsActor]
        } else {
          TestActorRef[DummyYouTubeUploader]
        }
      }
    }

    val youtubeManager =
      TestActorRef(new YouTubeManager(DummyYouTubeUploaderFactory, DummyYouTubeDetailsActorFactory, configuration) with TestInjectedActorSupport)
  }

  abstract class IntegrationTestScope extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()
    lazy val configuredYoutubeUploader = app.injector.instanceOf(BindingKey(classOf[ConfiguredYouTubeUploader.Factory]))
    lazy val configuredYoutubeDetailsActor = app.injector.instanceOf(BindingKey(classOf[ConfiguredYouTubeDetailsActor.Factory]))
    lazy val youtubeManager = TestActorRef(new YouTubeManager(configuredYoutubeUploader, configuredYoutubeDetailsActor, configuration))

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "Youtube Uploader Manager" should {

    // =================================================================================================================
    // Unit tests
    // =================================================================================================================
    "forward request to youtube uploader" in new UnitTestScope {
      configuration.get[Int]("youtube.actors.limit") returns 5
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager ! request

      expectMsg(request)
    }

    "forward request to youtube uploader to update video details" in new UnitTestScope {
      val request = VideoDetails("videoId", "title", Some("description"),
        List("tags"), "public", "Education")

      youtubeManager ! request

      expectMsg(request)
    }

    "forward request to youtube category actor" in new UnitTestScope {
      youtubeManager ! GetCategories

      expectMsg(List())
    }

    "not upload video if already 5 videos are uploading" in new UnitTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager.underlyingActor.noOfActors = 5

      youtubeManager ! request

      expectMsg("Cant upload any more videos parallely.")
    }

    "decrement no of actors when video upoad is done" in new UnitTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager ! request
      youtubeManager ! Done

      expectMsg(request)
      youtubeManager.underlyingActor.noOfActors mustBe 0
    }

    "decrement no of actors when video upload is cancelled" in new UnitTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager ! request
      youtubeManager ! Cancel

      expectMsg(request)
      youtubeManager.underlyingActor.noOfActors mustBe 0
    }

    // =================================================================================================================
    // Integration tests
    // =================================================================================================================
    "start uploading video" in new IntegrationTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager ! request

      expectMsg(request)
    }

    "update video details" in new IntegrationTestScope {
      val request = VideoDetails("videoId", "title", Some("description"),
        List("tags"), "public", "Education")

      youtubeManager ! request

      expectMsg(request)
    }

    "return categories" in new IntegrationTestScope {
      youtubeManager ! GetCategories

      expectMsg(List())
    }

    "not upload video if already 5 actors are working" in new IntegrationTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager.underlyingActor.noOfActors = 5

      youtubeManager ! request

      expectMsg("Cant upload any more videos parallely.")
    }

    "make an actor available if an upload is done" in new IntegrationTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager ! request
      youtubeManager ! Done

      youtubeManager.underlyingActor.noOfActors mustBe 0
    }

    "make an actor available if an upload is cancelled" in new IntegrationTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeManager ! request
      youtubeManager ! Cancel

      youtubeManager.underlyingActor.noOfActors mustBe 0
    }
  }

}
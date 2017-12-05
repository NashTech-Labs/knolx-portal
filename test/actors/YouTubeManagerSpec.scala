package actors

import java.io.FileInputStream

import actors.YouTubeDetailsActor.{GetCategories, GetDetails, UpdateVideoDetails}
import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
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
  val testConfig = Configuration(ConfigFactory.load("test.conf"))

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
      TestActorRef(new YouTubeManager(DummyYouTubeUploaderFactory, DummyYouTubeDetailsActorFactory, testConfig) with TestInjectedActorSupport)
  }

  abstract class IntegrationTestScope extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()
    lazy val configuredYoutubeUploader = app.injector.instanceOf(BindingKey(classOf[ConfiguredYouTubeUploader.Factory]))
    lazy val configuredYoutubeDetailsActor = app.injector.instanceOf(BindingKey(classOf[ConfiguredYouTubeDetailsActor.Factory]))
    lazy val youtubeManager = TestActorRef(new YouTubeManager(configuredYoutubeUploader, configuredYoutubeDetailsActor, testConfig))

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
    "forward request to details actor to update video details" in new UnitTestScope {
      val request = UpdateVideoDetails("videoId", "title", Some("description"),
        List("tags"), "public", "Education")

      youtubeManager ! request

      expectMsg(request)
    }

    "forward request to youtube uploader to upload video" in new UnitTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), "27", "public", 10L)

      youtubeManager ! request

      expectMsg(request)
    }

    "forward request to youtube details actor for getting the categories" in new UnitTestScope {
      youtubeManager ! GetCategories

      expectMsg(List())
    }

    "forward request to youtube details actor to get the details of video" in new UnitTestScope {
      youtubeManager ! GetDetails("videoId")

      expectMsg(GetDetails("videoId"))
    }

    "do nothing for unrecognized message" in new UnitTestScope {
      youtubeManager ! "Unrecognized Message"

      expectNoMsg()
    }

    // =================================================================================================================
    // Integration tests
    // =================================================================================================================
    "start uploading video" in new IntegrationTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), "27", "public", 10L)

      youtubeManager ! request

      expectMsg(request)
    }

    "update video details" in new IntegrationTestScope {
      val request = UpdateVideoDetails("videoId", "title", Some("description"),
        List("tags"), "public", "Education")

      youtubeManager ! request

      expectMsg(request)
    }

    "return categories" in new IntegrationTestScope {
      youtubeManager ! GetCategories

      expectMsg(List())
    }

    "return details of video" in new UnitTestScope {
      youtubeManager ! GetDetails("videoId")

      expectMsg(GetDetails("videoId"))
    }
  }

}
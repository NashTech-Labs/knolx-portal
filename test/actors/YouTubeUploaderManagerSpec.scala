package actors

import java.io.{FileInputStream, InputStream}

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import helpers.{DummyYouTubeDetailsActor, DummyYouTubeUploader}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.matcher.AnyBeHaveMatchers
import play.api.libs.Files
import play.api.libs.concurrent.InjectedActorSupport
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}


class YouTubeUploaderManagerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with WordSpecLike with DefaultAwaitTimeout with FutureAwaits with MustMatchers
  with Mockito with ImplicitSender with BeforeAndAfterAll {

  private val sessionId = "SessionId"

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

    val youtubeUploaderManager =
      TestActorRef(new YouTubeUploaderManager(DummyYouTubeUploaderFactory, DummyYouTubeDetailsActorFactory) with TestInjectedActorSupport)
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "Youtube Uploader Manager" should {

    "forward request to youtube uploader" in new UnitTestScope {

      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeUploaderManager ! request

      expectMsg(request)
    }

    "forward request to youtube uploader to update video details" in new UnitTestScope {

      val request = VideoDetails("videoId", "title", Some("description"),
        List("tags"), "public", "Education")

      youtubeUploaderManager ! request

      expectMsg(request)
    }

    "forward request to youtube category actor" in new UnitTestScope {

      youtubeUploaderManager ! Categories

      expectMsg(List())
    }

    "not upload video if already 5 videos are uploading" in new UnitTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeUploaderManager.underlyingActor.noOfActors = 5

      youtubeUploaderManager ! request

      expectMsg("Cant upload any more videos parallely.")
    }

    "decrement no of actors" in new UnitTestScope {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val request = YouTubeUploader.Upload(sessionId, new FileInputStream(tempFile), "title", Some("description"),
        List("tags"), 10L)

      youtubeUploaderManager ! request
      youtubeUploaderManager ! "Done"

      youtubeUploaderManager.underlyingActor.noOfActors mustBe 0
    }
  }

}
package actors

import java.io.InputStream

import actors.YouTubeUploader.VideoDetails
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.api.services.youtube.model.{Video, VideoSnippet}
import com.google.inject.name.Names
import controllers.TestEnvironment
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import services.YoutubeService

class YouTubeUploaderSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  private val sessionId = "SessionId"
  private val title = "title"
  private val description = Some("description")

  private val tags = List("tag1", "tag2")
  private val status = "public"
  private val category = "category"

  private val inputStream = new InputStream {
    override def read(): Int = ???
  }

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {
    lazy val app: Application = fakeApp()

    val youtubeUploadManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploadManager")))))

    val youtubeService = mock[YoutubeService]
    val youtubeUploader =
      TestActorRef(new YouTubeUploader(youtubeUploadManager, youtubeService))
  }

  "Youtube Uploader" should {

    "upload video" in new TestScope {

      youtubeUploader ! YouTubeUploader.Upload(sessionId, inputStream, title, description, tags, 1L)

      expectNoMsg
    }

    "update video details" in new TestScope {
      val videoDetails = VideoDetails("videoId",
        title,
        description,
        tags,
        status,
        category)

      youtubeService.update(videoDetails) returns "Successfully updated the video details"

      youtubeUploader ! videoDetails

      expectMsg("Successfully updated the video details")
    }
  }
}

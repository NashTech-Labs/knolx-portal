package actors

import java.io.InputStream

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.{AbstractInputStreamContent, HttpRequestInitializer, InputStreamContent}
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoSnippet, VideoStatus}
import com.google.inject.name.Names
import helpers.TestEnvironment
import models.SessionsRepository
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

import scala.collection.JavaConverters._

class YouTubeUploaderSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  private val sessionId = "SessionId"
  private val titleOfVideo = "title"
  private val description = "description"

  private val tags = List("tag1", "tag2")
  private val status = "public"
  private val category = "category"

  private val inputStream = new InputStream {
    override def read(): Int = 1
  }

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {
    lazy val app: Application = fakeApp()

    val youtubeProgressManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeProgressManager")))))

    val youtubeManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeManager")))))

    val sessionsRepository = mock[SessionsRepository]
  }

  "Youtube Uploader" should {

    // =================================================================================================================
    // Unit tests
    // =================================================================================================================
    "upload video" in new TestScope with MockitoSugar {
      val youtube = mock[YouTube](Mockito.RETURNS_DEEP_STUBS)
      val inputStream = mock[InputStream]
      val videoInsert = mock[YouTube#Videos#Insert]
      val abstractIS = mock[AbstractInputStreamContent]
      val httpReqInit = mock[HttpRequestInitializer]

      val fileSize = 10L
      val videoSnippet = new VideoSnippet().setTitle(titleOfVideo).setDescription(description).setTags(tags.asJava)
      val video = new Video().setSnippet(videoSnippet).setStatus(new VideoStatus().setPrivacyStatus("private")).setId("videoId")
      val inputStreamContent = new InputStreamContent("video/*", inputStream).setLength(fileSize)
      val netHttpTransport = new NetHttpTransport
      val uploader = new MediaHttpUploader(abstractIS, netHttpTransport, httpReqInit)

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube){
          override def getVideoSnippet(title: String,
                                       description: Option[String],
                                       tags: List[String],
                                       categoryId: String = ""): VideoSnippet = videoSnippet

          override def getVideo(snippet: VideoSnippet, status: String, videoId: String = ""): Video = video

          override def getInputStreamContent(is: InputStream, fileSize: Long): InputStreamContent = inputStreamContent

          override def getMediaHttpUploader(videoInsert: YouTube#Videos#Insert, chunkSize: Int): MediaHttpUploader = uploader
        })

      when(youtube.videos().insert("snippet,statistics,status", video, inputStreamContent)) thenReturn videoInsert
      when(videoInsert.execute()) thenReturn video

      sessionsRepository.storeTemporaryVideoURL(sessionId, "www.youtube.com/embed/videoId")

      youtubeUploader ! YouTubeUploader.Upload(sessionId, inputStream, titleOfVideo, Some(description), tags, "27", "private", 1L)

      expectNoMsg
    }

    "Do nothing for any other random message" in new TestScope {

      val youtube = mock[YouTube]

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube))

      youtubeUploader ! "random message"

      expectNoMsg
    }

    "return video snippet for no category id" in new TestScope {
      val youtube = mock[YouTube]

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube))

      val result = youtubeUploader.underlyingActor.getVideoSnippet(titleOfVideo, Some(description), tags)

      result.getTitle must be equalTo titleOfVideo
    }

    "return video snippet when category id is not empty" in new TestScope {
      val youtube = mock[YouTube]

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube))

      val result = youtubeUploader.underlyingActor.getVideoSnippet(titleOfVideo, Some(description), tags, "27")

      result.getTitle must be equalTo titleOfVideo
    }

    "return video for no videoId" in new TestScope {
      val youtube = mock[YouTube]
      val videoSnippet = new VideoSnippet()

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube))

      val result = youtubeUploader.underlyingActor.getVideo(videoSnippet, "private")

      result.getStatus.getPrivacyStatus must be equalTo "private"
    }

    "return video for a given videoId" in new TestScope {
      val youtube = mock[YouTube]
      val videoSnippet = new VideoSnippet()

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube))

      val result = youtubeUploader.underlyingActor.getVideo(videoSnippet, "private", "videoId")

      result.getStatus.getPrivacyStatus must be equalTo "private"
    }

    "return input stream" in new TestScope {
      val youtube = mock[YouTube]
      val inputStream = mock[InputStream]

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube))

      val result = youtubeUploader.underlyingActor.getInputStreamContent(inputStream, 10L)

      result.getLength must be equalTo 10L
    }

    // =================================================================================================================
    // Integration tests
    // =================================================================================================================
    "start uploading video" in new TestScope with MockitoSugar {
      val youtube = mock[YouTube](Mockito.RETURNS_DEEP_STUBS)
      val inputStream = mock[InputStream]
      val videoInsert = mock[YouTube#Videos#Insert]
      val abstractIS = mock[AbstractInputStreamContent]
      val httpReqInit = mock[HttpRequestInitializer]

      val fileSize = 10L
      val videoSnippet = new VideoSnippet().setTitle(titleOfVideo).setDescription(description).setTags(tags.asJava)
      val video = new Video().setSnippet(videoSnippet).setStatus(new VideoStatus().setPrivacyStatus("private")).setId("videoId")
      val inputStreamContent = new InputStreamContent("video/*", inputStream).setLength(fileSize)
      val netHttpTransport = new NetHttpTransport
      val uploader = new MediaHttpUploader(abstractIS, netHttpTransport, httpReqInit)

      val youtubeUploader =
        TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeManager, sessionsRepository, youtube))

      when(youtube.videos().insert("snippet,statistics,status", video, inputStreamContent)) thenReturn videoInsert
      when(videoInsert.execute()) thenReturn video

      sessionsRepository.storeTemporaryVideoURL(sessionId, "www.youtube.com/embed/videoId")

      youtubeUploader ! YouTubeUploader.Upload(sessionId, inputStream, titleOfVideo, Some(description), tags, "27", "private", 1L)

      expectNoMsg
    }
  }
}

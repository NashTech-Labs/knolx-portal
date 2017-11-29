package actors

import java.io.InputStream

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http._
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.services.youtube.model.Video
import com.google.inject.name.Names
import helpers.TestEnvironment
import org.specs2.specification.Scope
import play.api.{Application, Logger}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

import scala.concurrent.duration._


class YouTubeUploadManagerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  private val sessionId = "SessionId"

  private val abstractIS = new InputStreamContent("", new InputStream {
    override def read(): Int = 1
  })

  private val requestInitializer = new HttpRequestInitializer {
    override def initialize(request: HttpRequest): Unit =
      Logger.info("Performing unit action")
  }

  private val mediaHttpUploader = new MediaHttpUploader(abstractIS, new NetHttpTransport, requestInitializer)

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {

    lazy val app: Application = fakeApp()

    val emailManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

    val youtubeUploadManager =
      TestActorRef(
        new YouTubeProgressManager {
          override def preStart(): Unit = {}
        })

  }

  "YouTubeUploadManager" should {

    "register an uploader for session" in new TestScope {

      youtubeUploadManager ! YouTubeProgressManager.RegisterUploadListener(sessionId, mediaHttpUploader)

      youtubeUploadManager.underlyingActor.sessionUploaders.keys.head must be equalTo "SessionId"
    }

    "cancel video upload for given session" in new TestScope {

      youtubeUploadManager ! YouTubeProgressManager.CancelVideoUpload(sessionId)

      youtubeUploadManager.underlyingActor.videoCancelStatus.get("SessionId") must be equalTo Some(true)
    }

    "add video for session to sessionVideos" in new TestScope {

      youtubeUploadManager ! YouTubeProgressManager.SessionVideo(sessionId, new Video().setId("videoId"))

      youtubeUploadManager.underlyingActor.sessionVideos(sessionId).getId must be equalTo "videoId"
    }

    "return no percentage when no video has been uploaded for the session" in new TestScope {

      val result: Option[Double] = await((youtubeUploadManager ? YouTubeProgressManager.VideoUploader(sessionId)) (5.seconds).mapTo[Option[Double]])

      result must beNone
    }

    "return 100 when video has been uploaded for the session" in new TestScope {

      youtubeUploadManager.underlyingActor.sessionUploadComplete += sessionId
      val result: Option[Double] = await((youtubeUploadManager ? YouTubeProgressManager.VideoUploader(sessionId)) (5.seconds).mapTo[Option[Double]])

      result must be equalTo Some(100D)
    }

    "return video for the session" in new TestScope {

      val result: Option[Video] = await((youtubeUploadManager ? YouTubeProgressManager.VideoId(sessionId))(5.seconds).mapTo[Option[Video]])

      result must beNone
    }
  }

}

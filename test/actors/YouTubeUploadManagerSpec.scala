package actors

import java.io.InputStream

import actors.SessionsScheduler.ScheduledSessions
import akka.actor.{ActorRef, ActorSystem, Scheduler}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http._
import com.google.api.services.youtube.model.Video
import com.google.inject.name.Names
import helpers.TestEnvironment
import models.{FeedbackFormsResponseRepository, SessionsRepository}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.bson.BSONObjectID
import utilities.DateTimeUtility

import scala.concurrent.duration._


class YouTubeUploadManagerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  private val sessionId = "SessionId"

  private val abstractIS = new InputStreamContent("", new InputStream {
    override def read(): Int = ???
  })

  private val requestInitializer = new HttpRequestInitializer {
    override def initialize(request: HttpRequest): Unit = ???
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
        new YouTubeUploadManager {
          override def preStart(): Unit = {}
        })

  }

  "YouTubeUploadManager" should {

    "register an uploader for session" in new TestScope {

      youtubeUploadManager ! YouTubeUploadManager.RegisterUploadListener(sessionId, mediaHttpUploader)

      youtubeUploadManager.underlyingActor.sessionUploaders.keys.head must be equalTo "SessionId"
    }

    "cancel video upload for given session" in new TestScope {

      youtubeUploadManager ! YouTubeUploadManager.CancelVideoUpload(sessionId)

      youtubeUploadManager.underlyingActor.videoCancelStatus.get("SessionId") must be equalTo Some(true)
    }

    "add video for session to sessionVideos" in new TestScope {

      youtubeUploadManager ! YouTubeUploadManager.SessionVideo(sessionId, new Video().setId("videoId"))

      youtubeUploadManager.underlyingActor.sessionVideos(sessionId).getId must be equalTo "videoId"
    }

    "return no percentage when no video has been uploaded for the session" in new TestScope {

      val result: Option[Double] = await((youtubeUploadManager ? YouTubeUploadManager.VideoUploader(sessionId)) (5.seconds).mapTo[Option[Double]])

      result must beNone
    }

    "return 100 when video has been uploaded for the session" in new TestScope {

      youtubeUploadManager.underlyingActor.sessionUploadComplete += sessionId
      val result: Option[Double] = await((youtubeUploadManager ? YouTubeUploadManager.VideoUploader(sessionId)) (5.seconds).mapTo[Option[Double]])

      result must be equalTo Some(100D)
    }

    /*"return 100 when video has been uploaded for the session and the uploader is still in sessionUploaders" in new TestScope {

      youtubeUploadManager.underlyingActor.sessionUploaders += sessionId -> mediaHttpUploader
      mediaHttpUploader.getUploadState returns UploadState.MEDIA_COMPLETE

      val result: Option[Double] = await((youtubeUploadManager ? YouTubeUploadManager.VideoUploader(sessionId)) (5.seconds).mapTo[Option[Double]])

      result must be equalTo Some(100D)
    }*/

    "return video for the session" in new TestScope {

      val result: Option[Video] = await((youtubeUploadManager ? YouTubeUploadManager.VideoId(sessionId))(5.seconds).mapTo[Option[Video]])

      result must beNone
    }
  }

}

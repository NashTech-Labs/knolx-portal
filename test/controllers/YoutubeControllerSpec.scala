package controllers

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Names
import helpers.{AddSessionUploader, RemoveSessionUploader, TestEnvironment}
import models.SessionsRepository
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.{BadPart, FilePart}
import play.api.mvc.{MultipartFormData, Results}
import play.api.test._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class YoutubeControllerSpec extends PlaySpecification with Results with Mockito {

  private val system = ActorSystem("TestActorSystem")
  private val sessionId = "SessionId"

  abstract class WithTestApplication extends TestEnvironment with Scope {
    lazy val app: Application = fakeApp()

    val sessionsRepository = mock[SessionsRepository]

    lazy val controller =
      new YoutubeController(
        knolxControllerComponent.messagesApi,
        knolxControllerComponent,
        sessionsRepository,
        youtubeManager,
        youtubeProgressManager
      )

    val youtubeProgressManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeProgressManager")))))
    val youtubeManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeManager")))))

  }

  "Youtube Controller" should {

    "upload video file" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("" -> Seq(""), "" -> Seq(""))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("file", "file", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      val request =
        FakeRequest(POST, "/youtube/" + sessionId + "/upload")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 200
    }

    "send bad request if video file not found in the request" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("" -> Seq(""), "" -> Seq(""))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("key", "filename", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      val request =
        FakeRequest(POST, "/youtube/" + sessionId + "/upload")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "send bad request if multipart form data is corrupted in the request" in new WithTestApplication {
      val request =
        FakeRequest(POST, "/youtube/" + sessionId + "/upload")
          .withHeaders(("fileSize", "10"))


      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "return Ok when asked for percentage of file" in new WithTestApplication {
      youtubeProgressManager ! AddSessionUploader(sessionId)

      val result = controller.getPercentageUploaded(sessionId)(FakeRequest(GET, "/youtube/sessionId/progress"))

      youtubeProgressManager ! RemoveSessionUploader(sessionId)

      status(result) must be equalTo 200
    }

    "cancel the upload of a video" in new WithTestApplication {
      val result = controller.cancel(sessionId)(FakeRequest(GET, "/youtube/sessionId/cancel"))

      status(result) must be equalTo 200
    }

    "get the video ID of a session" in new WithTestApplication {
      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future.successful(List("youtube/video/url"))
      val result = controller.getVideoId(sessionId)(FakeRequest(GET, "/youtube/sessionId/videoid"))

      status(result) must be equalTo 200
    }

    "return bad request while getting video ID of a session when videoID is not found" in new WithTestApplication {
      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future.successful(List())
      val result = controller.getVideoId(sessionId)(FakeRequest(GET, "/youtube/sessionId/videoid"))

      status(result) must be equalTo 400
    }

    "return bad request while getting video ID of a session when videoID is an empty string" in new WithTestApplication {
      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future.successful(List(""))
      val result = controller.getVideoId(sessionId)(FakeRequest(GET, "/youtube/sessionId/videoid"))

      status(result) must be equalTo 400
    }

    "return bad request for wrong json" in new WithTestApplication {
      private val wrongJson = Json.parse("""{"title":"title"} """)

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/youtube/sessionId/update").withBody(wrongJson))

      status(result) must be equalTo 400
    }

    "return bad request if no video URL is found for the session" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"title": "title",
          |	"description": "None",
          |	"tags": ["tag1", "tag2"],
          |	"status": "private",
          |	"category": "Education"
          |}""".stripMargin
      )

      sessionsRepository.getVideoURL(sessionId) returns Future(List())

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/youtube/:sessionId/update").withBody(jsonBody))

      status(result) must be equalTo 400
    }

    "update video details" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"title": "title",
          |	"description": "None",
          |	"tags": ["tag1", "tag2"],
          |	"status": "private",
          |	"category": "Education"
          |}""".stripMargin
      )

      sessionsRepository.getVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/youtube/sessionId/update").withBody(jsonBody))

      status(result) must be equalTo 200
    }

    "return ok if a video upload is currently going on" in new WithTestApplication {
      youtubeProgressManager ! AddSessionUploader(sessionId)

      val result = controller.checkIfUploading(sessionId)(FakeRequest(GET, "/youtube/sessionId/checkIfUploading"))

      youtubeProgressManager ! RemoveSessionUploader(sessionId)

      status(result) must be equalTo 200
    }

    "return bad request if a video upload is not currently going on" in new WithTestApplication {
      val result = controller.checkIfUploading(sessionId)(FakeRequest(GET, "/youtube/sessionId/checkIfUploading"))

      status(result) must be equalTo 400
    }

  }

}

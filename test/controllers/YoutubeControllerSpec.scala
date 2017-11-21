package controllers

import akka.actor.ActorRef
import akka.stream.{ActorMaterializer, Materializer}
import com.google.inject.name.Names
import helpers.TestEnvironment
import models.{ForgotPasswordRepository, SessionsRepository}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.Files
import play.api.libs.Files.{DefaultTemporaryFileCreator, TemporaryFile, TemporaryFileCreator}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc.MultipartFormData.{BadPart, FilePart}
import play.api.mvc.{MultipartFormData, Results}
import play.api.test.{FakeHeaders, FakeRequest, PlaySpecification, StubControllerComponentsFactory}
import play.api.test
import utilities.DateTimeUtility

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class YoutubeControllerSpec extends PlaySpecification with Results {

  val sessionId = "SessionId"

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    lazy val controller =
      new YoutubeController(
        knolxControllerComponent.messagesApi,
        knolxControllerComponent,
        sessionsRepository,
        youtubeUploaderManager,
        youtubeUploadManager
      )

    val sessionsRepository = mock[SessionsRepository]

    val youtubeUploadManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploadManager")))))

    val youtubeUploaderManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploaderManager")))))

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Youtube Controller" should {

    "upload video file" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("" -> Seq(""), "" -> Seq(""))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("file", "file", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      val request =
        FakeRequest(POST, "/youtube/sessionId/upload")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 200
    }

    "return Ok when asked for percentage of file" in new WithTestApplication {
      val result = controller.getPercentageUploaded(sessionId)(FakeRequest(GET, "/youtube/sessionId/progress"))

      status(result) must be equalTo 200
    }

    "cancel the upload of a video" in new WithTestApplication {
      val result = controller.cancel(sessionId)(FakeRequest(GET, "/youtube/sessionId/cancel"))

      status(result) must be equalTo 200
    }

    "get the video ID of a session" in new WithTestApplication {
      val result = controller.getVideoId(sessionId)(FakeRequest(GET, "/youtube/sessionId/videoid"))

      status(result) must be equalTo 200
    }

    "return bad request for wrong json" in new WithTestApplication {
      private val wrongJson = Json.parse("""{"title":"title"} """)

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/youtube/sessionId/update").withBody(wrongJson))

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

      sessionsRepository.getVideoURL(sessionId) returns Future(List())

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/youtube/:sessionId/update").withBody(jsonBody))

      status(result) must be equalTo 400
    }

  }

}

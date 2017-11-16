package controllers

import akka.actor.ActorRef
import akka.stream.{ActorMaterializer, Materializer}
import com.google.inject.name.Names
import models.{ForgotPasswordRepository, SessionsRepository}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.Files
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.libs.mailer.MailerClient
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Results}
import play.api.test.{FakeHeaders, FakeRequest, PlaySpecification}
import utilities.DateTimeUtility

/**
  * Created by knoldus on 15/11/17.
  */
class YoutubeControllerSpec extends PlaySpecification with Results {

  val sessionId = "SessionId"

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()
    lazy implicit val materializer = app.materializer
    lazy val controller =
      new YoutubeController(
        knolxControllerComponent.messagesApi,
        knolxControllerComponent,
        sessionsRepository,
        youtubeUploader,
        youtubeUploadManager,
        youtubeUploadProgress,
        materializer
      )

    val sessionsRepository = mock[SessionsRepository]

    val youtubeUploader =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploader")))))
    val youtubeUploadManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploadManager")))))
    val youtubeUploadProgress =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploadProgress")))))

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Youtube Controller" should {

    "send BadRequest if file not found" in new WithTestApplication {
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val part = FilePart[TemporaryFile](key = "image", filename = "the.file", contentType = Some("image/jpeg"), ref = tempFile)
      val formData = MultipartFormData(dataParts = Map(), files = Seq(part), badParts = Seq())

      val result = controller.upload(sessionId)(FakeRequest(POST, "/youtube/:sessionId/upload")
                                                .withHeaders(("fileSize", "10"))
                                                .withMultipartFormDataBody(formData))

      status(result) must be equalTo 200
    }
  }
}

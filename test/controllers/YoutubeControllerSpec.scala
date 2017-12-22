package controllers

import java.text.SimpleDateFormat

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Names
import helpers.{AddSessionUploader, TestEnvironment}
import models.{SessionsRepository, UserInfo}
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.{Application, Logger}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.{BadPart, FilePart}
import play.api.mvc.{MultipartFormData, Results}
import play.api.test._
import reactivemongo.bson.{BSONDateTime, BSONObjectID}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class YoutubeControllerSpec extends PlaySpecification with Results with Mockito {

  private val system = ActorSystem("TestActorSystem")
  private val sessionId = "SessionId"
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")

  private val _id: BSONObjectID = BSONObjectID.generate()
  private val emailObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))

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
      val parameters = Map[String, Seq[String]]("title" -> Seq("title"),
        "description" -> Seq("description"),
        "tags[]" -> Seq("tag1, tag2, tag3"),
        "status" -> Seq("private"),
        "category" -> Seq("category"))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("file", "file", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 200
    }

    "send bad request if video file not found in the request" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("title" -> Seq("title"),
        "description" -> Seq("description"),
        "tags[]" -> Seq("tag1"),
        "status" -> Seq("private"),
        "category" -> Seq("category"))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("key", "filename", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, Seq[FilePart[Files.TemporaryFile]](), Seq[BadPart]())

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "send bad request if multipart form data is corrupted in the request" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withFormUrlEncodedBody(("title", "title"),
            ("description", "description"),
            ("tags[]", "tag1, tag2"),
            ("status", "private"),
            ("category", "education"))


      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "send bad request if status field is not provided" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("title" -> Seq("title"),
        "description" -> Seq("description"),
        "tags[]" -> Seq("tag1"),
        "category" -> Seq("category"))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("key", "filename", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "send bad request if category field is not provided" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("title" -> Seq("title"),
        "description" -> Seq("description"),
        "tags[]" -> Seq("tag1"),
        "status" -> Seq("status"))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("key", "filename", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "send bad request if tags field is not provided" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("title" -> Seq("title"),
        "description" -> Seq("description"),
        "status" -> Seq("status"),
        "category" -> Seq("category"))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("key", "filename", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "send bad request if title field is not provided" in new WithTestApplication {
      val parameters = Map[String, Seq[String]](
        "description" -> Seq("description"),
        "tags" -> Seq("tag1"),
        "status" -> Seq("status"),
        "category" -> Seq("category"))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("key", "filename", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "send bad request if description field is not provided" in new WithTestApplication {
      val parameters = Map[String, Seq[String]]("title" -> Seq("title"),
        "tags" -> Seq("tag1"),
        "status" -> Seq("status"),
        "category" -> Seq("category"))
      val tempFile = Files.SingletonTemporaryFileCreator.create("prefix", "suffix")
      val files = Seq[FilePart[TemporaryFile]](FilePart("key", "filename", Some("multipart/form-data"), tempFile))
      val multipartBody = MultipartFormData(parameters, files, Seq[BadPart]())

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val request =
        FakeRequest(POST, "/uploadapi/" + sessionId + "/upload")
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withHeaders(("fileSize", "10"))
          .withMultipartFormDataBody(multipartBody)

      val result = controller.upload(sessionId)(request)

      status(result) must be equalTo 400
    }

    "return 0 percentage if upload of file hasn't yet started" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.getPercentageUploaded(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/progress")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "return percentage when asked for percentage of file" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      youtubeProgressManager ! AddSessionUploader(sessionId)

      val result = controller.getPercentageUploaded(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/progress")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "cancel the upload of a video" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.cancel(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/cancel")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "get the video ID of a session" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future.successful(List("youtube/video/url"))

      val result = controller.getVideoId(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/videoid")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "return bad request while getting video ID of a session when videoID is not found" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future.successful(List())

      val result = controller.getVideoId(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/videoid")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "return bad request while getting video ID of a session when videoID is an empty string" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future.successful(List(""))

      val result = controller.getVideoId(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/videoid")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "return bad request for wrong json" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      private val wrongJson = Json.parse("""{"title":"title"} """)

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(wrongJson)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

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

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List())

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/:sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

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

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "not update video details if title is not provided" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"description": "None",
          |	"tags": ["tag1", "tag2"],
          |	"status": "private",
          |	"category": "Education"
          |}""".stripMargin
      )

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "not update video details if description is not provided" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"title": "title",
          |	"tags": ["tag1", "tag2"],
          |	"status": "private",
          |	"category": "Education"
          |}""".stripMargin
      )

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "not update video details if tags are not provided" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"title": "title",
          |	"description": "None",
          |	"status": "private",
          |	"category": "Education"
          |}""".stripMargin
      )

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "not update video details if status is not provided" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"title": "title",
          |	"description": "None",
          |	"tags": ["tag1", "tag2"],
          |	"category": "Education"
          |}""".stripMargin
      )

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "not update video details if category is not provided" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"title": "title",
          |	"description": "None",
          |	"tags": ["tag1", "tag2"],
          |	"status": "private"
          |}""".stripMargin
      )

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "not update video details if no video URL exists" in new WithTestApplication {
      private val jsonBody = Json.parse(
        """{
          |	"title": "title",
          |	"description": "None",
          |	"tags": ["tag1", "tag2"],
          |	"status": "private",
          |	"category": "Education"
          |}""".stripMargin
      )

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getVideoURL(sessionId) returns Future(List())

      val result = controller.updateVideo(sessionId)(FakeRequest(POST, "/uploadapi/sessionId/update")
        .withBody(jsonBody)
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "return ok if a video upload is currently going on" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      youtubeProgressManager ! AddSessionUploader(sessionId)

      val result = controller.checkIfUploading(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/checkIfUploading")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "return bad request if a video upload is not currently going on" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.checkIfUploading(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/checkIfUploading")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "return ok if temporary URL exists" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future(List("dummy/youtube/videoId"))

      val result = controller.checkIfTemporaryUrlExists(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/checkiftemporaryurlexists")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 200
    }

    "return bad request if temporary URL does not exist" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future(List())

      val result = controller.checkIfTemporaryUrlExists(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/checkiftemporaryurlexists")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

    "return bad request if temporary URL exists as an ampty string in DB" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      sessionsRepository.getTemporaryVideoURL(sessionId) returns Future(List(""))

      val result = controller.checkIfTemporaryUrlExists(sessionId)(FakeRequest(GET, "/uploadapi/sessionId/checkiftemporaryurlexists")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo 400
    }

  }

}

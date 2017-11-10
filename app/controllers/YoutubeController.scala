package controllers

import java.io.FileInputStream
import javax.inject.{Inject, Named}

import actors.{RemoveVideoUploader, VideoUploader, YouTubeUploadManager, YouTubeUploader}
import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.duration._
import akka.stream.Materializer
import akka.util.Timeout
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class YoutubeController @Inject()(messagesApi: MessagesApi,
                                  controllerComponents: KnolxControllerComponents,
                                  @Named("YouTubeUploader") youtubeManager: ActorRef,
                                  @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef,
                                  @Named("YouTubeUploadProgress") youtubeUploadProgress: ActorRef,
                                  implicit val mat: Materializer
                                 ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val timeout = Timeout(100 seconds)

  def upload(sessionId: String /*, fileSize: Long*/): Action[MultipartFormData[TemporaryFile]] = Action(parse.multipartFormData) { request =>
    Logger.info("Called uploadFile function" + request)
    request.body.file("file").fold {
      BadRequest("Something went wrong while uploading the file. Please try again.")
    } { videoFile =>
      val fileSize = request.headers.get("fileSize").getOrElse("0").toLong
      Logger.info("-------------------------File size = " + fileSize)
      youtubeManager ! YouTubeUploader.Upload(sessionId, new FileInputStream(videoFile.ref), "title", Some("description"), List("tags"), fileSize)
      Ok("Uploading started!")
    }
  }

  def getUploader(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (youtubeUploadProgress ? VideoUploader(sessionId)).mapTo[Option[MediaHttpUploader]]
      .map { maybeUploader =>
        maybeUploader.fold {
          Ok(Json.toJson(0L).toString)
        } { uploader =>
          uploader.getUploadState match {
            case UploadState.MEDIA_COMPLETE => Ok("Upload Completed!")
            case _                          => Ok(Json.toJson(uploader.getProgress).toString)
          }
        }
      }
  }

  def cancel(sessionId: String): Action[AnyContent] = action { implicit request =>
    youtubeUploadManager ! YouTubeUploadManager.CancelVideoUpload(sessionId)
    youtubeUploadProgress ! RemoveVideoUploader(sessionId)

    Ok("Upload cancelled!")
  }

  def getVideoId(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (youtubeManager ? YouTubeUploader.VideoId(sessionId)).mapTo[Option[String]]
      .map { maybeVideoId =>
        maybeVideoId.fold {
          BadRequest("No Video ID found for the given session")
        } { videoId =>
          Ok(Json.toJson(videoId).toString())
        }
      }
  }

  def checkIfUploading(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (youtubeUploadProgress ? VideoUploader(sessionId)).mapTo[Option[MediaHttpUploader]]
      .map { maybeUploader =>
        maybeUploader.fold {
          BadRequest("Not Uploading any video for current session")
        } { uploader =>
          uploader.getUploadState match {
            case UploadState.MEDIA_COMPLETE => BadRequest("Not Uploading any video for current session")
            case _                          => Ok("Video is uploading")
          }
        }
      }
  }

}

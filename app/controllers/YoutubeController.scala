package controllers

import java.io.FileInputStream
import javax.inject.{Inject, Named, Singleton}

import actors.{RemoveVideoUploader, VideoUploader, YouTubeUploadManager, YouTubeUploader}
import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.duration._
import akka.stream.Materializer
import akka.util.Timeout
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState
import com.google.api.services.youtube.model.Video
import models.SessionsRepository
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class UpdateVideoDetails(title: String,
                              description: Option[String],
                              tags: List[String],
                              status: String,
                              category: String)

@Singleton
class YoutubeController @Inject()(messagesApi: MessagesApi,
                                  controllerComponents: KnolxControllerComponents,
                                  sessionsRepository: SessionsRepository,
                                  @Named("YouTubeUploader") youtubeManager: ActorRef,
                                  @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef,
                                  @Named("YouTubeUploadProgress") youtubeUploadProgress: ActorRef,
                                  implicit val mat: Materializer
                                 ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val timeout = Timeout(100 seconds)

  implicit val questionInformationFormat: OFormat[UpdateVideoDetails] = Json.format[UpdateVideoDetails]

  def upload(sessionId: String): Action[MultipartFormData[TemporaryFile]] = Action(parse.multipartFormData) { request =>
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
    (youtubeManager ? YouTubeUploader.VideoId(sessionId)).mapTo[Option[Video]]
      .map { maybeVideo =>
        maybeVideo.fold {
          BadRequest("No Video ID found for the given session")
        } { video =>
          Ok(Json.toJson(video.getId).toString())
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

  def updateVideo(sessionId: String): Action[JsValue] = action(parse.json).async { implicit request =>
    request.body.validate[UpdateVideoDetails].fold(
      jsonValidationError => {
        Logger.error("Json validation error occurred ----- " + jsonValidationError)
        Future.successful(BadRequest("Something went wrong while updating the form"))
      },
      updateVideoDetails => {
        sessionsRepository.getVideoURL(sessionId).flatMap { listOfVideoIds =>
          val maybeVideoId = listOfVideoIds.headOption
          maybeVideoId.fold {
            Future.successful(BadRequest("No video found for this session"))
          } { videoURL =>
            val videoId = videoURL.split("/")(2)
            (youtubeManager ? YouTubeUploader.VideoDetails(videoId,
              updateVideoDetails.title,
              updateVideoDetails.description,
              updateVideoDetails.tags,
              updateVideoDetails.status,
              updateVideoDetails.category)).mapTo[String]
              .map(Ok(_))
          }
        }
      }
    )
  }

}

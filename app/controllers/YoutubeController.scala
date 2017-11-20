package controllers

import java.io.FileInputStream
import javax.inject.{Inject, Named, Singleton}

import actors.YouTubeUploadManager.VideoUploader
import actors.{RemoveVideoUploader, YouTubeUploadManager, YouTubeUploader}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.google.api.services.youtube.model.Video
import models.SessionsRepository
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

case class UpdateVideoDetails(title: String,
                              description: Option[String],
                              tags: List[String],
                              status: String,
                              category: String)

@Singleton
class YoutubeController @Inject()(messagesApi: MessagesApi,
                                  controllerComponents: KnolxControllerComponents,
                                  sessionsRepository: SessionsRepository,
                                  @Named("YouTubeUploaderManager") youtubeUploaderManager: ActorRef,
                                  @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef
                                 )/*(implicit val mat: Materializer, ec: ExecutionContext)*/ extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val timeout = Timeout(100 seconds)

  implicit val questionInformationFormat: OFormat[UpdateVideoDetails] = Json.format[UpdateVideoDetails]

  def upload(sessionId: String): Action[MultipartFormData[TemporaryFile]] = Action(parse.multipartFormData).async { request =>
    Logger.info("Called uploadFile function" + request)
    request.body.file("file").fold {
      Future.successful(BadRequest("Something went wrong while uploading the file. Please try again."))
    } { videoFile =>
      val fileSize = request.headers.get("fileSize").getOrElse("0").toLong
      Logger.info("-------------------------File size = " + fileSize)
      (youtubeUploaderManager ? YouTubeUploader.Upload(sessionId, new FileInputStream(videoFile.ref), "title", Some("description"), List("tags"), fileSize))
        .map(_ => Ok("Uploading started!"))
    }
  }

  def getPercentageUploaded(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (youtubeUploadManager ? VideoUploader(sessionId)).mapTo[Option[Double]]
      .map { maybePercentage =>
        maybePercentage.fold {
          BadRequest("No video is being uploaded for the given session")
        } { percentage =>
          Ok(Json.toJson(percentage).toString)
        }
      }
  }

  def cancel(sessionId: String): Action[AnyContent] = action { implicit request =>
    youtubeUploadManager ! YouTubeUploadManager.CancelVideoUpload(sessionId)

    Ok("Upload cancelled!")
  }

  def getVideoId(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (youtubeUploadManager ? YouTubeUploadManager.VideoId(sessionId)).mapTo[Option[Video]]
      .map { maybeVideo =>
        maybeVideo.fold {
          BadRequest("No Video ID found for the given session")
        } { video =>
          Ok(Json.toJson(video.getId).toString())
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
            (youtubeUploaderManager ? YouTubeUploader.VideoDetails(videoId,
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

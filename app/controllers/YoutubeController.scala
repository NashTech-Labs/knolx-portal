package controllers

import java.io.FileInputStream
import javax.inject.{Inject, Named, Singleton}

import actors.YouTubeProgressManager.GetUploadProgress
import actors.{YouTubeDetailsActor, YouTubeProgressManager, YouTubeUploader}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import models.SessionsRepository
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
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
                                  @Named("YouTubeManager") youtubeManager: ActorRef,
                                  @Named("YouTubeProgressManager") youtubeProgressManager: ActorRef
                                 ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val timeout = Timeout(10.seconds)

  implicit val questionInformationFormat: OFormat[UpdateVideoDetails] = Json.format[UpdateVideoDetails]

  def upload(sessionId: String): Action[AnyContent] = adminAction.async { request =>
    request.body.asMultipartFormData.fold {
      Logger.error(s"Something went wrong when getting multipart form data")

      Future.successful(BadRequest("Something went wrong while uploading the file. Please try again!"))
    } { multiPartFormData =>
      multiPartFormData.file("file").fold {
        Logger.error(s"Something went wrong when getting file part from multipart form data")

        Future.successful(BadRequest("Something went wrong while uploading the file. Please try again!"))
      } { videoFile =>
        val fileSize = request.headers.get("fileSize").getOrElse("0").toLong
        val title = request.headers.get("title").getOrElse("title")
        val description = request.headers.get("description").getOrElse("description")
        val tags = request.headers.get("tags").getOrElse("tags").split(",").toList
        val categoryId = request.headers.get("category").getOrElse("27")
        val status = request.headers.get("status").getOrElse("private")

        youtubeManager ! YouTubeUploader.Upload(sessionId, new FileInputStream(videoFile.ref), title, Some(description), tags, categoryId, status, fileSize)

        Future.successful(Ok("Uploading has started"))
      }
    }
  }

  def getPercentageUploaded(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    (youtubeProgressManager ? YouTubeProgressManager.GetUploadProgress(sessionId)).mapTo[Option[Double]]
      .map { maybePercentage =>
        maybePercentage.fold {
          Ok(Json.toJson(0D))
        } { percentage =>
          Ok(Json.toJson(percentage))
        }
      }
  }

  def checkIfUploading(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    (youtubeProgressManager ? GetUploadProgress(sessionId)).mapTo[Option[Double]]
      .map { maybePercentage =>
        maybePercentage.fold {
          BadRequest("No video is being uploaded for the given session")
        } { _ =>
          Ok("Video is currently being uploaded")
        }
      }
  }

  def cancel(sessionId: String): Action[AnyContent] = adminAction { implicit request =>
    youtubeProgressManager ! YouTubeProgressManager.CancelVideoUpload(sessionId)

    Ok("Upload cancelled!")
  }

  def getVideoId(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository.getTemporaryVideoURL(sessionId).map { listOfURL =>
      val maybeVideoURL = listOfURL.headOption
      maybeVideoURL.fold {
        BadRequest("No new video URL found")
      } { videoURL =>
        if (videoURL.trim.isEmpty) {
          BadRequest("No new video URL found")
        } else {
          val videoId = videoURL.split("/")(2)
          Ok(videoId)
        }
      }
    }
  }

  def updateVideo(sessionId: String): Action[JsValue] = adminAction(parse.json).async { implicit request =>
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
            (youtubeManager ? YouTubeDetailsActor.UpdateVideoDetails(videoId,
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

  def checkIfTemporaryUrlExists(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository.getTemporaryVideoURL(sessionId).map { listOfURL =>
      val maybeVideoURL = listOfURL.headOption
      maybeVideoURL.fold {
        BadRequest("No new video URL found")
      } { videoURL =>
        if (videoURL.trim.isEmpty) {
          BadRequest("No new video URL found")
        } else {
          val videoId = videoURL.split("/")(2)
          Ok(videoId)
        }
      }
    }
  }
}

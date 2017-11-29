package controllers

import java.io.FileInputStream
import javax.inject.{Inject, Named, Singleton}

import actors.YouTubeProgressManager.VideoUploader
import actors.{UpdateVideoDetails, YouTubeProgressManager, YouTubeUploader}
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.google.api.services.youtube.model.Video
import models.SessionsRepository
import play.api.Logger
import play.api.data.Forms._
import play.api.data.Form
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

case class UpdateVideoInfo(sessionId: String,
                           videoId: String,
                           title: String,
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

  val updateVideoDetailsForm = Form(
    mapping(
      "sessionId" -> nonEmptyText,
      "videoId" -> nonEmptyText,
      "title" -> nonEmptyText,
      "description" -> optional(nonEmptyText),
      "tags" -> list(text),
      "status" -> nonEmptyText,
      "category" -> nonEmptyText
    )(UpdateVideoInfo.apply)(UpdateVideoInfo.unapply)
  )

  def upload(sessionId: String): Action[AnyContent] = action.async { request =>
    request.body.asMultipartFormData.fold {
      Logger.error(s"Something went wrong when getting multipart form data")

      Future.successful(BadRequest("Something went wrong while uploading the file. Please try again!"))
    } { multiPartFormData =>
      multiPartFormData.file("file").fold {
        Logger.error(s"Something went wrong when getting file part from multipart form data")

        Future.successful(BadRequest("Something went wrong while uploading the file. Please try again!"))
      } { videoFile =>
        val fileSize = request.headers.get("fileSize").getOrElse("0").toLong

        youtubeManager ! YouTubeUploader.Upload(sessionId, new FileInputStream(videoFile.ref), "title", Some("description"), List("tags"), fileSize)

        Future.successful(Ok("Uploader Set"))
      }
    }
  }

  def getPercentageUploaded(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (youtubeProgressManager ? VideoUploader(sessionId)).mapTo[Option[Double]]
      .map { maybePercentage =>
        maybePercentage.fold {
          BadRequest("No video is being uploaded for the given session")
        } { percentage =>
          Ok(Json.toJson(percentage).toString)
        }
      }
  }

  def cancel(sessionId: String): Action[AnyContent] = action { implicit request =>
    youtubeProgressManager ! YouTubeProgressManager.CancelVideoUpload(sessionId)

    Ok("Upload cancelled!")
  }

  def getVideoId(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (youtubeProgressManager ? YouTubeProgressManager.VideoId(sessionId)).mapTo[Option[Video]]
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
            (youtubeManager ? UpdateVideoDetails(videoId,
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

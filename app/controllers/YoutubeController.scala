package controllers

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Paths
import javax.inject.{Inject, Named}

import actors.{YouTubeUploadManager, YouTubeUploader}
import akka.actor.ActorRef
import akka.stream.Materializer
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.mvc._

class YoutubeController @Inject()(messagesApi: MessagesApi,
                                  controllerComponents: KnolxControllerComponents,
                                  @Named("YouTubeUploader") youtubeManager: ActorRef,
                                  @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef,
                                  implicit val mat: Materializer
                                 ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  def upload(sessionId: String): Action[MultipartFormData[TemporaryFile]] = Action(parse.multipartFormData) { request =>
    Logger.info("Called uploadFile function" + request)
    request.body.file("file").map { picture =>
      youtubeManager ! YouTubeUploader.Upload(sessionId, new FileInputStream(picture.ref), "title", Some("description"), List("tags"))
      Ok("File uploaded")
    }.getOrElse {
      BadRequest("Missing file")
    }
  }

  def cancel(sessionId: String): Action[AnyContent] = action { implicit request =>
    youtubeUploadManager ! YouTubeUploadManager.CancelVideoUpload(sessionId)

    Ok("Upload cancelled!")
  }

}

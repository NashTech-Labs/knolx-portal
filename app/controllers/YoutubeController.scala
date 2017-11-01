package controllers

import java.io.FileInputStream
import javax.inject.{Inject, Named}

import actors.{YouTubeUploader, YouTubeUploadManager}
import akka.actor.ActorRef
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files
import play.api.mvc.{Action, AnyContent}

class YoutubeController @Inject()(messagesApi: MessagesApi,
                                  controllerComponents: KnolxControllerComponents,
                                  @Named("YouTubeUploader") youtubeManager: ActorRef,
                                  @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef
                                 ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  def upload(sessionId: String): Action[Files.TemporaryFile] = action(parse.temporaryFile) { implicit request =>
    val file = request.body.path.toFile

    youtubeManager ! YouTubeUploader.Upload(sessionId, new FileInputStream(file), "title", Some("description"), List("tags"))

    Ok("Uploading started!")
  }

  def cancel(sessionId: String): Action[AnyContent] = action { implicit request =>
    youtubeUploadManager ! YouTubeUploadManager.CancelVideoUpload(sessionId)

    Ok("Upload cancelled!")
  }

}

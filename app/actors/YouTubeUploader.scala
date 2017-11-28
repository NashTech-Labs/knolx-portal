package actors

import java.io.InputStream
import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef}
import com.google.api.services.youtube.model.Video
import play.api.Logger
import services.YoutubeService

object ConfiguredYouTubeUploader {

  trait Factory {
    def apply(): Actor
  }

}

object YouTubeUploader {

  // Commands for YouTubeUploader actor
  case class Upload(sessionId: String,
                    is: InputStream,
                    title: String,
                    description: Option[String],
                    tags: List[String],
                    fileSize: Long)

}

class YouTubeUploader @Inject()(@Named("YouTubeUploadManager") youtubeUploadManager: ActorRef,
                               @Named("YouTubeUploaderManager") youtubeUploaderManager: ActorRef,
                                youtubeService: YoutubeService) extends Actor {

  var videoCancelStatus: Map[String, Boolean] = Map.empty
  var sessionVideos: Map[String, Video] = Map.empty

  def receive: Receive = {
    case YouTubeUploader.Upload(sessionId, is, title, description, tags, fileSize) =>
      upload(sessionId, is, title, description, tags, fileSize)
    case msg                                                                       =>
      Logger.info(s"Received a message in YouTubeUploader that cannot be handled $msg")
  }

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String],
             fileSize: Long): Unit = {
    Logger.info(s"Starting video upload for session $sessionId")

    youtubeService.upload(sessionId, is, title, description, tags, fileSize, sender())
    youtubeUploaderManager ! Done

  }

}

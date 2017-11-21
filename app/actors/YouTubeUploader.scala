package actors

import java.io.InputStream
import javax.inject.{Inject, Named}

import actors.YouTubeUploader._
import akka.actor.{Actor, ActorRef}
import com.google.api.client.http.InputStreamContent
import com.google.api.services.youtube.model.{Video, VideoSnippet, VideoStatus}
import play.api.Logger
import services.YoutubeService

import scala.collection.JavaConverters._

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

  case class VideoDetails(videoId: String,
                          title: String,
                          description: Option[String],
                          tags: List[String],
                          status: String,
                          category: String)

}

class YouTubeUploader @Inject()(@Named("YouTubeUploadManager") youtubeUploaderManager: ActorRef,
                                /*@Named("YouTubeUploadProgress") youtubeUploadProgress: ActorRef,*/
                                youtubeService: YoutubeService) extends Actor {

  var videoCancelStatus: Map[String, Boolean] = Map.empty
  var sessionVideos: Map[String, Video] = Map.empty

  def receive: Receive = {
    case YouTubeUploader.Upload(sessionId, is, title, description, tags, fileSize) => upload(sessionId, is, title, description, tags, fileSize)
    case videoDetails: YouTubeUploader.VideoDetails                                => sender() ! update(videoDetails)
    case msg                                                                       =>
      Logger.info(s"Received a message in YouTubeUploader that cannot be handled $msg")
  }

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String],
             fileSize: Long): Video = {
    Logger.info(s"Starting video upload for session $sessionId")

    youtubeService.upload(sessionId, is, title, description, tags, fileSize, sender())
  }

  def update(videoDetails: VideoDetails): String = youtubeService.update(videoDetails)

}

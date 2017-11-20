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
                                @Named("YouTubeUploadProgress") youtubeUploadProgress: ActorRef,
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

    val snippet = new VideoSnippet().setTitle(title).setDescription(description.getOrElse("")).setTags(tags.asJava)
    val videoObjectDefiningMetadata = new Video().setSnippet(snippet).setStatus(youtubeService.status)
    val mediaContent = new InputStreamContent(youtubeService.videoFileFormat, is).setLength(fileSize)

    val videoInsert = youtubeService.youtube.videos().insert(youtubeService.part, videoObjectDefiningMetadata, mediaContent)
    val uploader = videoInsert.getMediaHttpUploader.setDirectUploadEnabled(false).setChunkSize(256 * 0x400)

    //youtubeUploadProgress ! Uploader(sessionId, uploader)
    youtubeUploaderManager ! YouTubeUploadManager.RegisterUploadListener(sessionId, uploader)
    sender() ! "Uploader set"

    val video = videoInsert.execute()

    //sessionVideos += sessionId -> video
    youtubeUploaderManager ! YouTubeUploadManager.SessionVideo(sessionId, video)

    video
  }

  def update(videoDetails: VideoDetails): String = {

    val video = new Video

    val snippet = new VideoSnippet()
      .setTitle(videoDetails.title)
      .setDescription(videoDetails.description.getOrElse(""))
      .setTags(videoDetails.tags.asJava)
      .setCategoryId(videoDetails.category)
    val videoStatus = new VideoStatus().setPrivacyStatus(videoDetails.status)

    video.setSnippet(snippet).setStatus(videoStatus).setId(videoDetails.videoId)

    val videoUpdate = youtubeService.youtube.videos().update(youtubeService.part, video)
    try {
      videoUpdate.execute()
      "Successfully updated the video details"
    } catch {
      case error: Throwable =>
        "Something went wrong while updating the video details" + error + "-------------Video ID = " + videoDetails.videoId
    }
  }

}

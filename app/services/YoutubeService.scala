package services

import java.io.InputStream
import javax.inject.{Inject, Named}

import actors.{VideoDetails, YouTubeUploadManager}
import akka.actor.ActorRef
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model._
import controllers.UpdateVideoDetails
import play.api.Logger

class YoutubeService @Inject()(
                                @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef,
                                youtubeConfig: YoutubeConfiguration
                              ) {

  private lazy val youtube = youtubeConfig.youtube
  private val chunkSize = 1024 * 0x400
  lazy val part: String = youtubeConfig.part

  private val status = "private"

  def getVideoDetails(videoId: String): Option[UpdateVideoDetails] = {
    val listToExecute: YouTube#Videos#List = youtube.videos().list(part).setId(videoId)

    youtubeConfig.getVideoDetails(listToExecute).headOption
  }

  def getCategoryList: List[VideoCategory] = {
    val listToExecute = youtube
      .videoCategories()
      .list("snippet")
      .setRegionCode("IN")

    youtubeConfig.executeCategoryList(listToExecute)
  }

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String],
             fileSize: Long,
             sender: ActorRef): Video = {
    Logger.info(s"Starting video upload for session $sessionId")

    val snippet = youtubeConfig.getVideoSnippet(title, description, tags)
    val videoObjectDefiningMetadata = youtubeConfig.getVideo(snippet, status)
    val mediaContent = youtubeConfig.getInputStreamContent(is, fileSize)

    val videoInsert = youtube.videos().insert(part, videoObjectDefiningMetadata, mediaContent)
    val uploader = youtubeConfig.getMediaHttpUploader(videoInsert, chunkSize)

    youtubeUploadManager ! YouTubeUploadManager.RegisterUploadListener(sessionId, uploader)
    sender ! "Uploader set"

    val video = videoInsert.execute()

    youtubeUploadManager ! YouTubeUploadManager.SessionVideo(sessionId, video)

    video
  }

  def update(videoDetails: VideoDetails): String = {
    val snippet =
      youtubeConfig.getVideoSnippet(
        videoDetails.title,
        videoDetails.description,
        videoDetails.tags,
        videoDetails.category)

    val video = youtubeConfig.getVideo(snippet, videoDetails.status, videoDetails.videoId)

    val videoUpdate = youtube.videos().update(part, video)
    try {
      videoUpdate.execute()
      "Successfully updated the video details"
    } catch {
      case error: Throwable =>
        "Something went wrong while updating the video details" + error + "-------------Video ID = " + videoDetails.videoId
    }
  }
}

package services

import java.io.InputStream
import javax.inject.{Named, Inject}

import actors.{VideoDetails, YouTubeUploadManager}
import akka.actor.ActorRef
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.InputStreamContent
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{VideoStatus, VideoSnippet, Video, VideoCategory}
import controllers.UpdateVideoDetails
import play.api.Logger

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class YoutubeService1 @Inject()(youtube: YouTube) {

  private val chunkSize = 1024 * 0x400
  private val part = "snippet,statistics,status"
  private val videoFileFormat = "video/*"
  private val status = "private"

  def getCategoryList: List[VideoCategory] =
    youtube
      .videoCategories()
      .list("snippet")
      .setRegionCode("IN")
      .execute()
      .getItems
      .toList

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String],
             fileSize: Long,
             uploadManager: ActorRef): Video = {
    Logger.info(s"Starting video upload for session $sessionId")

    val snippet = getVideoSnippet(title, description, tags)
    val videoObjectDefiningMetadata = getVideo(snippet, status)
    val mediaContent = getInputStreamContent(is, fileSize)

    val videoInsert = youtube.videos().insert(part, videoObjectDefiningMetadata, mediaContent)
    val uploader = getMediaHttpUploader(videoInsert, chunkSize)

    uploadManager ! YouTubeUploadManager.RegisterUploadListener(sessionId, uploader)

    val video = videoInsert.execute()

    video
  }

  def update(videoDetails: VideoDetails): Video = {
    val snippet =
      getVideoSnippet(
        videoDetails.title,
        videoDetails.description,
        videoDetails.tags,
        videoDetails.category)

    val video = getVideo(snippet, videoDetails.status, videoDetails.videoId)

    val videoUpdate = youtube.videos().update(part, video)

    videoUpdate.execute()
  }

  def getVideoSnippet(title: String,
                      description: Option[String],
                      tags: List[String],
                      categoryId: String = ""): VideoSnippet = {
    val videoSnippet =
      new VideoSnippet()
        .setTitle(title)
        .setDescription(description.getOrElse(""))
        .setTags(tags.asJava)

    if (categoryId.isEmpty) videoSnippet else videoSnippet.setCategoryId(categoryId)
  }

  def getVideo(snippet: VideoSnippet, status: String, videoId: String = ""): Video = {
    val video =
      new Video()
        .setSnippet(snippet)
        .setStatus(new VideoStatus().setPrivacyStatus(status))

    if (videoId.isEmpty) video else video.setId(videoId)
  }

  def getInputStreamContent(is: InputStream, fileSize: Long): InputStreamContent =
    new InputStreamContent(videoFileFormat, is).setLength(fileSize)

  def getMediaHttpUploader(videoInsert: YouTube#Videos#Insert, chunkSize: Int): MediaHttpUploader =
    videoInsert
      .getMediaHttpUploader
      .setDirectUploadEnabled(false)
      .setChunkSize(chunkSize)

}

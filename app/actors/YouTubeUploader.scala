package actors

import java.io.InputStream
import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef}
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.InputStreamContent
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoSnippet, VideoStatus}
import models.SessionsRepository
import play.api.Logger
import reactivemongo.api.commands.UpdateWriteResult

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ConfiguredYouTubeUploader {

  trait Factory {
    def apply(): Actor
  }

}

object YouTubeUploader {

  // Commands for YouTubeUploader actor
  case class Upload(sessionId: String,
                    is: InputStream,
                    title: String = "title",
                    description: Option[String] = None,
                    tags: List[String] = List("tags"),
                    categoryId: String = "27",
                    status:String = "private",
                    fileSize: Long)

}

class YouTubeUploader @Inject()(@Named("YouTubeProgressManager") youtubeProgressManager: ActorRef,
                                @Named("YouTubeManager") youtubeManager: ActorRef,
                                sessionsRepository: SessionsRepository,
                                youtube: YouTube) extends Actor {
  private val chunkSize = 1024 * 0x400
  private val part = "snippet,statistics,status"
  private val videoFileFormat = "video/*"

  var videoCancelStatus: Map[String, Boolean] = Map.empty
  var sessionVideos: Map[String, Video] = Map.empty

  def receive: Receive = {
    case YouTubeUploader.Upload(sessionId, is, title, description, tags, categoryId, status, fileSize) =>
      upload(sessionId, is, title, description, tags, categoryId, status, fileSize)
    case msg                                                                       =>
      Logger.info(s"Received a message in YouTubeUploader that cannot be handled $msg")
  }

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String],
             categoryId: String,
             status: String,
             fileSize: Long): Future[UpdateWriteResult] = {
    Logger.info(s"Starting video upload for session $sessionId")

    val snippet = getVideoSnippet(title, description, tags, categoryId)
    val videoObjectDefiningMetadata = getVideo(snippet, status)
    val mediaContent = getInputStreamContent(is, fileSize)

    val videoInsert = youtube.videos().insert(part, videoObjectDefiningMetadata, mediaContent)
    val uploader = getMediaHttpUploader(videoInsert, chunkSize)

    youtubeProgressManager ! YouTubeProgressManager.RegisterUploadListener(sessionId, uploader)

    val video = videoInsert.execute()

    val videoURL = "www.youtube.com/embed/" + video.getId

    Logger.info(s"Sending video url $videoURL to the database for updation")

    sessionsRepository.storeTemporaryVideoURL(sessionId, videoURL)
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

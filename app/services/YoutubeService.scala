package services

import java.io.{File, FileInputStream, InputStream, InputStreamReader}
import java.util
import javax.inject.{Inject, Named}

import actors.{VideoDetails, YouTubeUploadManager}
import akka.actor.ActorRef
import com.google.api.client.auth.oauth2.{Credential, StoredCredential}
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.{DataStore, FileDataStoreFactory}
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model._
import com.google.common.collect.Lists
import play.api.Logger

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

class YoutubeService @Inject()(
                                @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef,
                                youtubeConfig: YoutubeConfiguration
                              ) {

  private lazy val youtube = youtubeConfig.youtube
  private val chunkSize = 256 * 0x400
  lazy val part = youtubeConfig.part

  private val videoFileFormat = "video/*"
  private val status = "private"

  def getCategoryList: List[VideoCategory] = {
    val listToExecute = youtube
      .videoCategories()
      .list("snippet")
      .setRegionCode("IN")

    youtubeConfig.execute(listToExecute)
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

package Services

import java.io.{FileInputStream, IOException}
import java.util.Calendar

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState
import com.google.api.client.googleapis.media.{MediaHttpUploader, MediaHttpUploaderProgressListener}
import com.google.api.client.http.InputStreamContent
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoSnippet, VideoStatus}
import com.google.common.collect.Lists
import play.api.Logger
import utilities.Auth

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class YoutubeService extends MediaHttpUploaderProgressListener {

  def uploadVideo(filePath: String): Future[Unit] = Future {
    val scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload")

    try {

      val credential: Credential = Auth.authorize(scopes, "uploadvideo")

      val youtube = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, credential)
        .setApplicationName("Knolx Portal")
        .build()

      Logger.info("Uploading: " + filePath)

      val videoObjectDefiningMetadata = new Video()

      val status = new VideoStatus()

      status.setPrivacyStatus("public")

      videoObjectDefiningMetadata.setStatus(status)

      val snippet = new VideoSnippet()

      val cal = Calendar.getInstance()
      snippet.setTitle("Test Upload via Java on " + cal.getTime)
      snippet.setDescription(
        "Video uploaded via YouTube Data API V3 using the Java library " + "on " + cal.getTime)

      // Set the keyword tags that you want to associate with the video.
      val tags = List("test", "example", "java", "YouTube Data API V3", "erase me").asJava
      snippet.setTags(tags)

      videoObjectDefiningMetadata.setSnippet(snippet)

      val mediaContent = new InputStreamContent("video/*",
        new FileInputStream(filePath))

      val videoInsert = youtube.videos()
        .insert("snippet,statistics,status", videoObjectDefiningMetadata, mediaContent)


      val uploader: MediaHttpUploader = videoInsert.getMediaHttpUploader

      uploader.setDirectUploadEnabled(false)

      uploader.setProgressListener(this)


      val returnedVideo = videoInsert.execute()

      Logger.info("\n================== Returned Video ==================\n")
      Logger.info("  - Id: " + returnedVideo.getId)
      Logger.info("  - Title: " + returnedVideo.getSnippet.getTitle)
      Logger.info("  - Tags: " + returnedVideo.getSnippet.getTags)
      Logger.info("  - Privacy Status: " + returnedVideo.getStatus.getPrivacyStatus)
      Logger.info("  - Video Count: " + returnedVideo.getStatistics.getViewCount)

    } catch {

      case e: GoogleJsonResponseException => Logger.error("GoogleJsonResponseException code: " + e.getDetails.getCode + " : "
        + e.getDetails.getMessage)
        e.printStackTrace()

      case e: IOException                 => Logger.error("IOException: " + e.getMessage)
        e.printStackTrace()

      case e: Throwable => Logger.error("Throwable: " + e.getMessage)
        e.printStackTrace()
    }
  }

  override def progressChanged(uploader: MediaHttpUploader): Unit = uploader.getUploadState match {
    case UploadState.INITIATION_STARTED =>
      Logger.info("Initiation Started")

    case UploadState.INITIATION_COMPLETE =>
      Logger.info("Initiation Completed")
    case UploadState.MEDIA_IN_PROGRESS   =>
      Logger.info("Upload in progress")
      Logger.info("Upload percentage: " + uploader.getNumBytesUploaded)
    case UploadState.MEDIA_COMPLETE      =>
      Logger.info("Upload Completed!")

    case UploadState.NOT_STARTED =>
      Logger.info("Upload Not Started!")

  }

}

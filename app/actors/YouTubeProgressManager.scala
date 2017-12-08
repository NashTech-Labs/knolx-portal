package actors

import actors.YouTubeProgressManager.YoutubeUploadException
import akka.actor.Actor
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState
import com.google.api.client.googleapis.media.{MediaHttpUploader, MediaHttpUploaderProgressListener}
import com.google.api.services.youtube.model.Video
import play.api.Logger

import scala.collection.mutable.ListBuffer

object YouTubeProgressManager {

  // Commands for YouTubeProgressManager actor
  case class RegisterUploadListener(sessionId: String, uploader: MediaHttpUploader)

  case class CancelVideoUpload(sessionId: String)

  case class GetUploadProgress(sessionId: String)

  case class SessionVideo(sessionId: String, video: Video)

  case class VideoId(sessionId: String)

  // Youtube actor exceptions
  class YoutubeUploadException(message: String) extends Exception(message)

}

class YouTubeProgressManager extends Actor {

  var videoCancelStatus: Map[String, Boolean] = Map.empty
  var sessionUploaders: Map[String, MediaHttpUploader] = Map.empty
  var sessionUploadComplete: ListBuffer[String] = ListBuffer.empty
  var sessionVideos: Map[String, Video] = Map.empty

  def receive: Receive = {
    case YouTubeProgressManager.RegisterUploadListener(sessionId, uploader) =>
      Logger.info(s"Registering and uploader for session $sessionId")
      addSessionUploader(sessionId, uploader)
      setProgressListener(sessionId, uploader)
    case YouTubeProgressManager.CancelVideoUpload(sessionId)                =>
      Logger.info(s"Cancelling video upload for session $sessionId")
      cancelVideoUpload(sessionId)
    case YouTubeProgressManager.SessionVideo(sessionId, video)              =>
      Logger.info("Adding to sessionVideos")
      sessionVideos += sessionId -> video
    case YouTubeProgressManager.GetUploadProgress(sessionId)                =>
      sender() ! uploadProgress(sessionId)
    case YouTubeProgressManager.VideoId(sessionId)                          =>
      Logger.info("Getting from sessionVideos")
      sender() ! sessionVideos.get(sessionId)
    case msg                                                                =>
      Logger.info(s"Received a message in YouTubeProgressManager that cannot be handled $msg")
  }

  def setProgressListener(sessionId: String, uploader: MediaHttpUploader): MediaHttpUploader =
    uploader.setProgressListener(new MediaHttpUploaderProgressListener() {
      def progressChanged(uploader: MediaHttpUploader) {
        uploader.getUploadState match {
          case UploadState.INITIATION_STARTED  => Logger.info(s"Video upload for session $sessionId initiated")
          case UploadState.INITIATION_COMPLETE =>
            addVideoCancelStatus(sessionId)
            if (videoCancelStatus(sessionId)) {
              Logger.info(s"Stopping video upload for session $sessionId")
              removeVideoCancelStatus(sessionId)
              removeSessionUploader(sessionId)
              // this is bad but the only way to cancel video upload for now
              throw new YoutubeUploadException(s"Video upload cancelled for session $sessionId")
            }
            Logger.info(s"Video upload initialization for session $sessionId completed")
          case UploadState.MEDIA_IN_PROGRESS   =>
            Logger.info(s"Video upload in progress for session $sessionId percentage ${uploader.getNumBytesUploaded}")

            if (videoCancelStatus(sessionId)) {
              Logger.info(s"Stopping video upload for session $sessionId")
              removeVideoCancelStatus(sessionId)
              removeSessionUploader(sessionId)
              // this is bad but the only way to cancel video upload for now
              throw new YoutubeUploadException(s"Video upload cancelled for session $sessionId")
            }
          case UploadState.MEDIA_COMPLETE      =>
            Logger.info(s"Video upload for session $sessionId completed")
            removeVideoCancelStatus(sessionId)
            sessionUploadComplete += sessionId
            removeSessionUploader(sessionId)
          case UploadState.NOT_STARTED         => Logger.info(s"Video upload for session $sessionId not started")
        }
      }
    })

  def addVideoCancelStatus(sessionId: String): Unit =
    videoCancelStatus += sessionId -> false

  def cancelVideoUpload(sessionId: String): Unit =
    videoCancelStatus += sessionId -> true

  def removeVideoCancelStatus(sessionId: String): Unit =
    videoCancelStatus -= sessionId

  def addSessionUploader(sessionId: String, uploader: MediaHttpUploader): Unit =
    sessionUploaders += sessionId -> uploader

  def removeSessionUploader(sessionId: String): Unit =
    sessionUploaders -= sessionId

  /**
    * None means no upload is currently going on for a particular session.
    * None can also mean that a uploader is not yet registered but the upload might have been initialized by a user
    */
  def uploadProgress(sessionId: String): Option[Double] =
    sessionUploaders.get(sessionId).fold {
      if (sessionUploadComplete.contains(sessionId)) {
        sessionUploadComplete -= sessionId
        Some(100D)
      } else {
        None
      }
    } { uploader =>
      uploader.getUploadState match {
        case UploadState.MEDIA_COMPLETE => Some(100D)
        case _                          => Some(uploader.getProgress * 100)
      }
    }

}

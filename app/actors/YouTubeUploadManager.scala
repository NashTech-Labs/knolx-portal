package actors

import actors.YouTubeUploadManager.YoutubeUploadException
import akka.actor.Actor
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState
import com.google.api.client.googleapis.media.{MediaHttpUploaderProgressListener, MediaHttpUploader}
import play.api.Logger

object YouTubeUploadManager {

  // Commands for YouTubeUploadManager actor
  case class RegisterUploadListener(sessionId: String, uploader: MediaHttpUploader)
  case class CancelVideoUpload(sessionId: String)

  // Youtube actor exceptions
  class YoutubeUploadException(message: String) extends Exception(message)

}

class YouTubeUploadManager extends Actor {

  var videoCancelStatus: Map[String, Boolean] = Map.empty

  def receive: Receive = {
    case YouTubeUploadManager.RegisterUploadListener(sessionId, uploader) =>
      Logger.info(s"Registering and uploader for session $sessionId")
      setProgressListener(sessionId, uploader)
    case YouTubeUploadManager.CancelVideoUpload(sessionId)                =>
      Logger.info(s"Cancelling video upload for session $sessionId")
      cancelVideoUpload(sessionId)
    case msg                                                              =>
      Logger.info(s"Received a message in YouTubeUploadManager that cannot be handled $msg")
  }

  def setProgressListener(sessionId: String, uploader: MediaHttpUploader): MediaHttpUploader =
    uploader.setProgressListener(new MediaHttpUploaderProgressListener() {
      def progressChanged(uploader: MediaHttpUploader) {
        uploader.getUploadState match {
          case UploadState.INITIATION_STARTED  => Logger.info(s"Video upload for session $sessionId initiated")
          case UploadState.INITIATION_COMPLETE =>
            addVideoUploadStatus(sessionId)
            Logger.info(s"Video upload initialization for session $sessionId completed")
          case UploadState.MEDIA_IN_PROGRESS   =>
            Logger.info(s"Video upload in progress for session $sessionId percentage ${uploader.getNumBytesUploaded}")

            if (videoCancelStatus(sessionId)) {
              Logger.info(s"Stopping video upload for session $sessionId")
              removeVideoUploadStatus(sessionId)
              // this is bad but the only way to cancel video upload for now
              throw new YoutubeUploadException(s"Video upload cancelled for session $sessionId")
            }
          case UploadState.MEDIA_COMPLETE      =>
            Logger.info(s"Video upload for session $sessionId completed")
            removeVideoUploadStatus(sessionId)
          case UploadState.NOT_STARTED         => Logger.info(s"Video upload for session $sessionId not started")
        }
      }
    })

  def addVideoUploadStatus(sessionId: String): Unit =
    videoCancelStatus += sessionId -> false

  def cancelVideoUpload(sessionId: String): Unit =
    videoCancelStatus += sessionId -> true

  def removeVideoUploadStatus(sessionId: String): Unit =
    videoCancelStatus -= sessionId

}

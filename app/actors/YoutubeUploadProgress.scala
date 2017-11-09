package actors

import akka.actor.Actor
import com.google.api.client.googleapis.media.MediaHttpUploader

case class VideoUploader(sessionId: String)
case class Uploader(sessionId: String, uploader: MediaHttpUploader)
case class RemoveVideoUploader(sessionId: String)

class YouTubeUploadProgress extends Actor {

  var sessionUploader: Map[String, MediaHttpUploader] = Map.empty

  override def receive: Receive = {
    case Uploader(sessionId, uploader) => addUploader(sessionId, uploader)
    case VideoUploader(sessionId) => sender() ! getUploader(sessionId)
    case RemoveVideoUploader(sessionId) => removeUploader(sessionId)
  }

  def addUploader(sessionId: String, uploader: MediaHttpUploader): Unit = sessionUploader += sessionId -> uploader

  def getUploader(sessionId: String): Option[MediaHttpUploader] = sessionUploader.get(sessionId)

  def removeUploader(sessionId: String): Unit = sessionUploader -= sessionId

}

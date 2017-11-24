package helpers

import actors._
import actors.SessionsScheduler.{CancelScheduledSession, GetScheduledSessions, ScheduleSession, ScheduledSessions}
import actors.UsersBanScheduler.GetScheduledBannedUsers
import akka.actor.Actor
import com.google.api.services.youtube.model.{Video, VideoCategory, VideoCategorySnippet}
import org.apache.commons.mail.EmailException
import play.api.Logger

class DummySessionsScheduler extends Actor {

  def receive: Receive = {
    case GetScheduledSessions              => sender ! ScheduledSessions(List.empty)
    case CancelScheduledSession(sessionId) => sender ! true
    case ScheduleSession(sessionId)        => sender ! true
  }

}

class DummyUsersBanScheduler extends Actor {

  def receive: Receive = {
    case GetScheduledBannedUsers => sender ! List.empty
  }

}

class TestEmailActor extends Actor {

  def receive: Receive = {
    case EmailActor.SendEmail(_, _, subject, _) if subject == "crash" => throw new EmailException
    case request: EmailActor.SendEmail                                => sender ! request
  }

}

class DummyYouTubeUploadManager extends Actor {

  override def receive: Receive = {
    case YouTubeUploadManager.VideoId(sessionId)               =>
      Logger.info("Getting from sessionVideos")
      sender() ! Some(new Video)
    case YouTubeUploadManager.VideoUploader(sessionId: String) => sender() ! Some(50D)
  }

}

class DummyYouTubeUploader extends Actor {

  override def receive: Receive = {
    case request: YouTubeUploader.Upload       => sender() ! request
  }

}

class DummyYouTubeDetailsActor extends Actor {

  override def receive: Receive = {
    case Categories => sender() ! List[VideoCategory]()
    case request: VideoDetails => sender() ! request
  }

}

class DummyYouTubeUploaderManager extends Actor {

  override def receive: Receive = {
    case request: YouTubeUploader.Upload       => sender() ! "Upload started"
    case request: VideoDetails => sender() ! "Updated video details"
    case Categories                            =>
      val videoCategorySnippet = new VideoCategorySnippet().setTitle("Education")
      sender() ! List(new VideoCategory().setSnippet(videoCategorySnippet).setId("12"))
    case _                                     => sender() ! "What?!?!?!"
  }
}

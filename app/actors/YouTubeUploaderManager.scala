package actors

import java.util.UUID
import javax.inject.Inject

import akka.actor.Actor
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport


class YouTubeUploaderManager @Inject()(
                                        configuredYouTubeUploader: ConfiguredYouTubeUploader.Factory,
                                        configuredYouTubeCategoryActor: ConfiguredYouTubeCategoryActor.Factory
                                      ) extends Actor with InjectedActorSupport {

  override def receive: Receive = {
    case request: YouTubeUploader.Upload =>
      val youTubeUploader = injectedChild(configuredYouTubeUploader(), s"YouTubeUploader-${UUID.randomUUID}")
      youTubeUploader forward request
    case request: YouTubeUploader.VideoDetails =>
      val youTubeUploader = injectedChild(configuredYouTubeUploader(), s"YouTubeUploader-${UUID.randomUUID}")
      youTubeUploader forward request
    case Categories                                                                       =>
      val youTubeCategoryActor = injectedChild(configuredYouTubeCategoryActor(), s"YouTubeCategoryActor-${UUID.randomUUID}")
      youTubeCategoryActor forward Categories
    case msg                                                                                       =>
      Logger.info(s"Received a message in YouTubeUploader that cannot be handled $msg")
  }
}

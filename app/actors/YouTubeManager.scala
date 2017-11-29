package actors

import java.util.UUID
import javax.inject.Inject

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Terminated, Actor, OneForOneStrategy}
import akka.routing.{RoundRobinRoutingLogic, Router, ActorRefRoutee}
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.InjectedActorSupport

class YouTubeManager @Inject()(
                                configuredYouTubeUploader: ConfiguredYouTubeUploader.Factory,
                                configuredYouTubeDetailsActor: ConfiguredYouTubeDetailsActor.Factory,
                                configuration: Configuration
                              ) extends Actor with InjectedActorSupport {

  lazy val limit: Int = configuration.get[Int]("youtube.actors.limit")

  var youtubeUploader = {
    val uploaders = Vector.fill(limit) {
      val uploader = injectedChild(configuredYouTubeUploader(), s"YouTubeUploader-${UUID.randomUUID}")
      context watch uploader
      ActorRefRoutee(uploader)
    }

    Router(RoundRobinRoutingLogic(), uploaders)
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy() {
      case ex: Exception =>
        Logger.error(s"Got an unknown exception from $sender while processing youtube request, $ex")
        Stop
    }

  override def receive: Receive = {
    case request: YouTubeUploader.Upload                 =>
      Logger.info(s"Forwarding request to upload video to one of the YouTubeUploader for session ${request.sessionId}")
      youtubeUploader.route(request, sender())
    case Terminated(uploader)                            =>
      Logger.info(s"Removing YouTubeUploader $uploader")
      youtubeUploader = youtubeUploader.removeRoutee(uploader)
      val newUploader = injectedChild(configuredYouTubeUploader(), s"YouTubeUploader-${UUID.randomUUID}")
      context watch newUploader
      youtubeUploader = youtubeUploader.addRoutee(newUploader)
    case request: YouTubeDetailsActor.UpdateVideoDetails =>
      val youTubeDetailsActor = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeDetailsActor forward request
    case YouTubeDetailsActor.GetCategories               =>
      val youTubeDetailsActor = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeDetailsActor forward YouTubeDetailsActor.GetCategories
    case request: YouTubeDetailsActor.GetDetails         =>
      val youTubeDetailsActor = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeDetailsActor forward request
    case msg                                             =>
      Logger.info(s"Received a message in YouTubeManager that cannot be handled $msg")
  }
}

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

  var youtubeDetailsRoutee = {
    val detailRoutees = Vector.fill(limit) {
      val detailRoutee = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      context watch detailRoutee
      ActorRefRoutee(detailRoutee)
    }

    Router(RoundRobinRoutingLogic(), detailRoutees)
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
      youtubeDetailsRoutee.route(request, sender())
    case YouTubeDetailsActor.GetCategories               =>
      youtubeDetailsRoutee.route(YouTubeDetailsActor.GetCategories, sender())
    case request: YouTubeDetailsActor.GetDetails         =>
      youtubeDetailsRoutee.route(request, sender())
    case Terminated(detailsRoutee)                       =>
      Logger.info(s"Removing YouTubeDetailsActor $youtubeDetailsRoutee")
      youtubeDetailsRoutee = youtubeDetailsRoutee.removeRoutee(detailsRoutee)
      val newYoutubeDetailsRoutee = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      context watch newYoutubeDetailsRoutee
      youtubeDetailsRoutee = youtubeDetailsRoutee.addRoutee(newYoutubeDetailsRoutee)
    case msg                                             =>
      Logger.info(s"Received a message in YouTubeManager that cannot be handled $msg")
  }
}

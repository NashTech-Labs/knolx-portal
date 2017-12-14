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

  override def receive: Receive = {
    case request: YouTubeUploader.Upload                 =>
      Logger.info(s"Forwarding request to upload video to one of the YouTubeUploader for session ${request.sessionId}")
      youtubeUploader.route(request, sender())
    case Terminated(routee)                              =>
      if (routee.path.name.contains("YouTubeUploader")) {
        Logger.info(s"Removing YouTubeUploader $routee")
        youtubeUploader = youtubeUploader.removeRoutee(routee)
        val newUploader = injectedChild(configuredYouTubeUploader(), s"YouTubeUploader-${UUID.randomUUID}")
        context watch newUploader
        youtubeUploader = youtubeUploader.addRoutee(newUploader)
      } else {
        Logger.info(s"Removing YouTubeDetailsActor $routee")
        youtubeDetailsRoutee = youtubeDetailsRoutee.removeRoutee(routee)
        val newYoutubeDetailsRoutee = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
        context watch newYoutubeDetailsRoutee
        youtubeDetailsRoutee = youtubeDetailsRoutee.addRoutee(newYoutubeDetailsRoutee)
      }
    case request: YouTubeDetailsActor.UpdateVideoDetails =>
      youtubeDetailsRoutee.route(request, sender())
    case YouTubeDetailsActor.GetCategories               =>
      youtubeDetailsRoutee.route(YouTubeDetailsActor.GetCategories, sender())
    case request: YouTubeDetailsActor.GetDetails         =>
      youtubeDetailsRoutee.route(request, sender())
    case msg                                             =>
      Logger.info(s"Received a message in YouTubeManager that cannot be handled $msg")
  }
}

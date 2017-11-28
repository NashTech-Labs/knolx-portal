package actors

import java.util.UUID
import javax.inject.Inject

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, OneForOneStrategy}
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.InjectedActorSupport

case object Done
case object Cancel

class YouTubeUploaderManager @Inject()(
                                        configuredYouTubeUploader: ConfiguredYouTubeUploader.Factory,
                                        configuredYouTubeDetailsActor: ConfiguredYouTubeDetailsActor.Factory,
                                        configuration: Configuration
                                      ) extends Actor with InjectedActorSupport {

  var noOfActors = 0
  lazy val limit: Int = configuration.get[Int]("youtube.actors.limit")

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy() {
      case ex: Exception =>
        Logger.error(s"Got an unknown exception from $sender while processing youtube request, $ex")
        Stop
    }

  override def receive: Receive = {
    case request: YouTubeUploader.Upload =>
      if (noOfActors < limit) {
        val youTubeUploader = injectedChild(configuredYouTubeUploader(), s"YouTubeUploader-${UUID.randomUUID}")
        noOfActors += 1
        youTubeUploader forward request
      } else {
        sender() ! "Cant upload any more videos parallely."
      }
    case Done                            => noOfActors -= 1
    case Cancel                          => noOfActors -= 1
    case request: VideoDetails           =>
      val youTubeDetailsActor = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeDetailsActor forward request
    case GetCategories                   =>
      val youTubeDetailsActor = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeDetailsActor forward GetCategories
    case request: GetDetails             =>
      val youTubeDetailsActor = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeDetailsActor forward request
    case msg                             =>
      Logger.info(s"Received a message in YouTubeUploaderManager that cannot be handled $msg")
  }
}

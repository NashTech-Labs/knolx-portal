package actors

import java.util.UUID
import javax.inject.Inject

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, OneForOneStrategy, Props}
import org.apache.commons.mail.EmailException
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport

class YouTubeUploaderManager @Inject()(
                                        configuredYouTubeUploader: ConfiguredYouTubeUploader.Factory,
                                        configuredYouTubeDetailsActor: ConfiguredYouTubeDetailsActor.Factory
                                      ) extends Actor with InjectedActorSupport {

  var noOfActors = 0
  val limit = 5

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy() {
      case ex: Exception      =>
        Logger.error(s"Got an unknown exception from $sender while processing youtube request, $ex")
        Stop
    }

  override def receive: Receive = {
    case request: YouTubeUploader.Upload =>
      if(noOfActors < limit) {
        Logger.info(s"Current number of actors is $noOfActors")
        val youTubeUploader = injectedChild(configuredYouTubeUploader(), s"YouTubeUploader-${UUID.randomUUID}")
        noOfActors+=1
        youTubeUploader forward request
      } else {
        Logger.info("Cant upload any more videos parallely.")
        sender() ! "Cant upload any more videos parallely."
      }
    case "Done" => noOfActors-=1
    case request: VideoDetails =>
      val youTubeUploader = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeUploader forward request
    case Categories                                                                       =>
      val youTubeDetailsActor = injectedChild(configuredYouTubeDetailsActor(), s"YouTubeDetailsActor-${UUID.randomUUID}")
      youTubeDetailsActor forward Categories
    case msg                                                                                       =>
      Logger.info(s"Received a message in YouTubeUploaderManager that cannot be handled $msg")
  }
}

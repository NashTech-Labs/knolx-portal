package actors

import java.util.UUID
import javax.inject.Inject

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, OneForOneStrategy}
import org.apache.commons.mail.EmailException
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport

class EmailManager @Inject()(
                              emailChildFactory: ConfiguredEmailActor.Factory
                            ) extends Actor with InjectedActorSupport {

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy() {
      case ex: EmailException =>
        Logger.error(s"Got an EmailException from $sender while sending email, $ex")
        Stop
      case ex: Exception      =>
        Logger.error(s"Got an unknown exception from $sender while sending email, $ex")
        Stop
    }

  override def receive: Receive = {
    case request: EmailActor.SendEmail =>
      val emailActor = injectedChild(emailChildFactory(), s"EmailActor-${UUID.randomUUID}")

      Logger.info(s"Got a request to send email to ${request.to}. Finding email actor and forwarding request.")

      emailActor forward request
    case msg                           => Logger.warn(s"Got an unhandled message $msg")
  }

}

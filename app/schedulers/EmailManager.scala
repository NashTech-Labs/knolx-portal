package schedulers

import javax.inject.Inject

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy}
import akka.routing.RoundRobinPool
import org.apache.commons.mail.EmailException
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.duration._

class EmailManager @Inject()(
                              emailChildFactory: ConfiguredEmailActor.Factory
                            ) extends Actor with ActorLogging with InjectedActorSupport {

  var emailActor: ActorRef = _

  override def preStart(): Unit = {
    emailActor = injectedChild(emailChildFactory(), "EmailActor", p => p.withRouter(RoundRobinPool(5)))
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(
      maxNrOfRetries = 3,
      withinTimeRange = 1.minute
    ) {
      case ex: EmailException =>
        log.error(s"Got an exception while sending email, $ex")
        Restart
      case ex: Exception      =>
        log.error(s"Got an unknown exception while sending email, $ex")
        Escalate
    }

  override def receive: Receive = {
    case request: EmailActor.SendEmail =>
      log.info(s"Got a request to send email to ${request.to}. Finding email actor and forwarding request.")

      emailActor forward request
    case msg                           => log.warning(s"Got an unhandled message $msg")
  }

}

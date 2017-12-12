package actors

import java.util.UUID
import javax.inject.Inject

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, OneForOneStrategy, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import org.apache.commons.mail.EmailException
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.InjectedActorSupport

class EmailManager @Inject()(
                              emailChildFactory: ConfiguredEmailActor.Factory,
                              configuration: Configuration
                            ) extends Actor with InjectedActorSupport {

  lazy val limit: Int = configuration.get[Int]("knolx.actors.limit")

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy() {
      case ex: EmailException =>
        Logger.error(s"Got an EmailException from $sender while sending email, $ex")
        Stop
      case ex: Exception      =>
        Logger.error(s"Got an unknown exception from $sender while sending email, $ex")
        Stop
    }

  var emailActor: Router = {
    val emailActors = Vector.fill(limit) {
      val emailChild = injectedChild(emailChildFactory(), s"EmailActor-${UUID.randomUUID}")
      context watch emailChild
      ActorRefRoutee(emailChild)
    }
    Router(RoundRobinRoutingLogic(), emailActors)
  }

  override def receive: Receive = {
    case request: EmailActor.SendEmail =>
      emailActor.route(request, sender())
    case Terminated(emailRoutee)       =>
      emailActor = emailActor.removeRoutee(emailRoutee)
      val newEmailActor = injectedChild(emailChildFactory(), s"EmailActor-${UUID.randomUUID}")
      context watch newEmailActor
      emailActor = emailActor.addRoutee(newEmailActor)
    case msg                           => Logger.warn(s"Got an unhandled message $msg")
  }

}

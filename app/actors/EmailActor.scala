package actors

import javax.inject.Inject

import actors.EmailActor._
import akka.actor.{Actor, ActorLogging}
import play.api.libs.mailer.{Email, MailerClient}

object ConfiguredEmailActor {

  trait Factory {
    def apply(): Actor
  }

}

object EmailActor {

  case class SendEmail(to: List[String],
                       from: String,
                       subject: String,
                       body: String)

}

class EmailActor @Inject()(mailerClient: MailerClient) extends Actor with ActorLogging {

  override def receive: Receive = {
    case SendEmail(to, from, subject, body) =>
      log.info(s"Got a request to send email $to")

      val email = Email(subject, from, to, bodyHtml = Some(body))
      val response = Option(mailerClient.send(email))

      sender ! response
    case msg                                => log.warning(s"Got an unhandled message $msg")
  }

}

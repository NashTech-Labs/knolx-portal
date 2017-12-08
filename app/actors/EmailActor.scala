package actors

import javax.inject.Inject

import actors.EmailActor._
import akka.actor.Actor
import play.api.Logger
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

class EmailActor @Inject()(mailerClient: MailerClient) extends Actor {

  override def receive: Receive = {
    case SendEmail(to, from, subject, body) =>
      Logger.info(s"Got a request in $self to send email $to from $from with subject $subject")
      Logger.info(s"===========================================================================")
      Logger.info(s"===========================================================================")
      Logger.info(s"Mailer client for sending email $to from $from with subject $subject is $mailerClient")
      val email = Email(subject, from, bcc = to, bodyHtml = Some(body))
      val response = Option(mailerClient.send(email))
      sender ! response

      context stop self
    case msg                                => Logger.warn(s"Got an unhandled message $msg")
  }

}

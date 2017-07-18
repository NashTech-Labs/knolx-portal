package actors

import actors.Session.SendEmail
import akka.actor.{Props, Scheduler, Actor}
import models.{FeedbackForm, SessionInfo}
import play.api.Logger
import play.api.libs.mailer.Email
import schedulers.SessionsScheduler.SendSessionFeedbackForm
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import Session._

object Session {

  def props(sessionId: String,
            delay: FiniteDuration,
            session: SessionInfo,
            feedbackForm: FeedbackForm): Props = Props(new Session(sessionId, delay, session, feedbackForm))

  case object SendEmail

  val ToEmail = "sidharth@knoldus.com"
  val FromEmail = "sidharth@knoldus.com"

}

class Session(id: String,
              startAfter: FiniteDuration,
              session: SessionInfo,
              feedbackForm: FeedbackForm) extends Actor {

  val sessionTimer = scheduler.scheduleOnce(startAfter, self, SendEmail)

  override def preStart(): Unit = Logger.info(s"Session with session id $id scheduled")

  override def postStop(): Unit = {
    sessionTimer.cancel()
  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive = {
    case SendEmail =>
      val email =
        Email(subject = s"${session.topic} Feedback Form",
          from = FromEmail,
          to = List(ToEmail),
          bodyHtml = None,
          bodyText = Some("Hello World"), replyTo = None)

      // val emailSent = mailerClient.send(email)
  }

}

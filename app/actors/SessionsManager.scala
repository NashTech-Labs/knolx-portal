package actors

import javax.inject.Inject

import actors.SessionsManager.StartSession
import akka.actor.{ActorRef, Actor}
import models.{FeedbackFormsRepository, SessionsRepository}
import play.api.Logger
import play.api.libs.mailer.MailerClient
import utilities.DateTimeUtility

object SessionsManager {

  final case class StartSession(sessionId: String)

  case object SessionStarted

}

class SessionsManager @Inject()(sessionsRepository: SessionsRepository,
                                feedbackFormsRepository: FeedbackFormsRepository,
                                mailerClient: MailerClient,
                                dateTimeUtility: DateTimeUtility) extends Actor {
  var sessionIdToActor = Map.empty[String, ActorRef]

  override def preStart(): Unit = Logger.info("SessionsManager started")

  override def postStop(): Unit = Logger.info("SessionsManager stopped")

  def receive: Receive = {
    case startMsg@StartSession(sessionId) =>
      // context.actorOf(Session.props(sessionId,))
  }

}

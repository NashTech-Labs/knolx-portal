package actors

import java.util.Date
import javax.inject.Inject

import akka.actor.{Actor, ActorRef, Cancellable, Scheduler}
import models.{FeedbackForm, FeedbackFormsRepository, SessionInfo, SessionsRepository}
import play.api.Logger
import play.api.libs.mailer.{Email, MailerClient}
import actors.SessionsScheduler._
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}

object SessionsScheduler {

  case object RefreshSessionsSchedulers
  case object GetScheduledSessions
  case class CancelScheduledSession(sessionId: String)
  case class ScheduleSession(sessionId: String)

  private[actors] case class ScheduleSessionsForToday(originalSender: ActorRef, eventualSessions: Future[List[SessionInfo]])
  private[actors] case class ScheduleSessions(originalSender: ActorRef)
  private[actors] case class StartSessionsScheduler(initialDelay: FiniteDuration, interval: FiniteDuration)
  private[actors] case class SendSessionFeedbackForm(session: SessionInfo, feedbackForm: FeedbackForm)

  sealed trait SessionsSchedulerResponse
  case object ScheduledSessionsRefreshed extends SessionsSchedulerResponse
  case object ScheduledSessionsNotRefreshed extends SessionsSchedulerResponse
  case class ScheduledSessions(sessionIds: List[String]) extends SessionsSchedulerResponse

  val ToEmail = "sidharth@knoldus.com"
  val FromEmail = "sidharth@knoldus.com"

}

class SessionsScheduler @Inject()(sessionsRepository: SessionsRepository,
                                  feedbackFormsRepository: FeedbackFormsRepository,
                                  mailerClient: MailerClient,
                                  dateTimeUtility: DateTimeUtility) extends Actor {

  var scheduledSessions: Map[String, Cancellable] = Map.empty

  override def preStart(): Unit = {
    val millis = dateTimeUtility.nowMillis
    val initialDelay = ((dateTimeUtility.endOfDayMillis + 61 * 1000) - millis).milliseconds
    Logger.info(s"Sessions scheduler will start after $initialDelay")

    self ! StartSessionsScheduler(initialDelay, 1.day)
    self ! ScheduleSessionsForToday(self, sessionsScheduledToday(millis))
  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive =
    initializingHandler orElse
      schedulingHandler orElse
      reconfiguringHandler orElse
      emailHandler orElse
      defaultHandler

  def initializingHandler: Receive = {
    case StartSessionsScheduler(initialDelay, interval)             =>
      Logger.info(s"Configuring sessions scheduler to run every day")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleSessions
      )(context.dispatcher)
    case ScheduleSessionsForToday(originalSender, eventualSessions) =>
      Logger.info(s"Scheduling today's sessions")
      val eventualScheduledSessions = scheduleSessions(eventualSessions)

      eventualScheduledSessions foreach { schedulers =>
        scheduledSessions = scheduledSessions ++ schedulers

        originalSender ! scheduledSessions.size
      }
  }

  def schedulingHandler: Receive = {
    case ScheduleSessions(originalSender) =>
      Logger.info(s"Starting schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsScheduledToday(dateTimeUtility.nowMillis)
      val eventualScheduledSessions = scheduleSessions(eventualSessions)

      eventualScheduledSessions foreach { schedulers =>
        scheduledSessions = scheduledSessions ++ schedulers

        originalSender ! scheduledSessions.size
      }
    case GetScheduledSessions             =>
      Logger.info(s"Following sessions are scheduled ${scheduledSessions.keys}")

      sender ! ScheduledSessions(scheduledSessions.keys.toList)
    case ScheduleSession(sessionId)       =>
      val originalSender = sender

      Logger.info(s"Rescheduling session $sessionId")

      val eventualSessions = sessionsRepository.getById(sessionId) map (_.toList)
      val eventualScheduledSessions = scheduleSessions(eventualSessions)

      eventualScheduledSessions foreach { schedulers =>
        scheduledSessions = scheduledSessions ++ schedulers

        Logger.info(s"All scheduled sessions in memory after adding $sessionId are ${scheduledSessions.keys}")

        originalSender ! scheduledSessions.get(sessionId).isDefined
      }

  }

  def reconfiguringHandler: Receive = {
    case RefreshSessionsSchedulers         =>
      Logger.info(s"Scheduled sessions in memory before refreshing $scheduledSessions")
      Logger.info(s"Refreshing schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val cancelled = scheduledSessions.forall { case (_, cancellable) => cancellable.cancel }

      if (scheduledSessions.isEmpty || (scheduledSessions.nonEmpty && cancelled)) {
        val eventualSessions = sessionsRepository.sessionsScheduledToday
        val eventualScheduledSessions = scheduleSessions(eventualSessions)
        eventualScheduledSessions foreach { feedbackSchedulers => scheduledSessions = feedbackSchedulers }

        sender ! ScheduledSessionsRefreshed
      } else {
        sender ! ScheduledSessionsNotRefreshed
      }
    case CancelScheduledSession(sessionId) =>
      Logger.info(s"Removing scheduler for session $sessionId")

      scheduledSessions.get(sessionId).exists(_.cancel) match {
        case true  => Logger.info(s"Scheduled session $sessionId successfully cancelled")
        case false => Logger.info(s"Scheduled session $sessionId was already cancelled")
      }

      scheduledSessions = scheduledSessions - sessionId

      Logger.info(s"All scheduled sessions in memory after removing $sessionId are ${scheduledSessions.keys}")

      sender ! scheduledSessions.get(sessionId).isEmpty
  }

  def emailHandler: Receive = {
    case SendSessionFeedbackForm(session, feedbackForm) =>
      val email =
        Email(subject = s"${session.topic} Feedback Form",
          from = FromEmail,
          to = List(ToEmail),
          bodyHtml = None,
          bodyText = Some("Hello World"), replyTo = None)

      val emailSent = mailerClient.send(email)

      Logger.info(s"Email for session ${session.session} sent result $emailSent removing feedback form scheduler now")

      scheduledSessions = scheduledSessions - session._id.stringify
  }

  def defaultHandler: Receive = {
    case msg: Any =>
      Logger.error(s"Received a message $msg in Sessions Scheduler which cannot be handled")
  }

  def scheduleSessions(eventualSessions: Future[List[SessionInfo]]): Future[Map[String, Cancellable]] =
    eventualSessions flatMap { sessions =>
      Logger.info(s"Scheduling sessions today booked at ${sessions.map(session => new Date(session.date.value))}")

      Future.sequence {
        sessions map { session =>
          val eventualMaybeFeedbackForm = feedbackFormsRepository.getByFeedbackFormId(session.feedbackFormId)
          val delay = (session.date.value - dateTimeUtility.nowMillis).milliseconds

          eventualMaybeFeedbackForm map { maybeFeedbackForm =>
            maybeFeedbackForm.fold[Option[(String, Cancellable)]] {
              Logger.error(s"Something went wrong while getting feedback ${session.feedbackFormId}")
              None
            } { feedbackForm =>
              Some(session._id.stringify -> scheduler.scheduleOnce(delay, self, SendSessionFeedbackForm(session, feedbackForm)))
            }
          }
        }
      }
    } map (_.flatten.toMap)

  def sessionsScheduledToday(millis: Long): Future[List[SessionInfo]] =
    sessionsRepository.sessionsScheduledToday map { sessions =>
      sessions collect { case session
        if new Date(session.date.value).after(new Date(millis)) => session
      }
    }

}

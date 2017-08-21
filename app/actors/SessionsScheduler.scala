package actors

import java.time.LocalDateTime
import java.util.Date
import javax.inject.{Inject, Named}

import actors.SessionsScheduler._
import akka.actor.{Actor, ActorRef, Cancellable, Scheduler}
import controllers.routes
import models.SessionJsonFormats.{ExpiringNext, SchedulingNext}
import models.{FeedbackFormsRepository, SessionInfo, SessionsRepository, UsersRepository}
import play.api.{Configuration, Logger}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import akka.pattern.pipe

object SessionsScheduler {

  // messages used for getting/reconfiguring schedulers/scheduled-emails
  case object RefreshSessionsSchedulers
  case object GetScheduledSessions
  case class CancelScheduledSession(sessionId: String)
  case class ScheduleSession(sessionId: String)

  // messages used internally for starting session schedulers/emails
  case object ScheduleFeedbackEmailsStartingTomorrow
  case object ScheduleFeedbackRemindersStartingTomorrow
  private[actors] case class ScheduleFeedbackEmailsStartingToday(originalSender: ActorRef, eventualSessions: Future[List[SessionInfo]])
  private[actors] case class InitiateFeedbackEmailsStartingTomorrow(initialDelay: FiniteDuration, interval: FiniteDuration)
  private[actors] case class ScheduleFeedbackRemindersStartingToday(originalSender: ActorRef, eventualSessions: Future[List[SessionInfo]])
  private[actors] case class InitialFeedbackRemindersStartingTomorrow(initialDelay: FiniteDuration, interval: FiniteDuration)
  private[actors] case class EventualScheduledEmails(scheduledMails: Map[String, Cancellable])
  private[actors] case class SendEmail(session: List[SessionInfo], reminder: Boolean)

  // messages used for responding back with current schedulers state
  sealed trait SessionsSchedulerResponse
  case object ScheduledSessionsRefreshed extends SessionsSchedulerResponse
  case object ScheduledSessionsNotRefreshed extends SessionsSchedulerResponse
  case class ScheduledSessions(sessionIds: List[String]) extends SessionsSchedulerResponse

}

case class EmailInfo(topic: String, presenter: String, date: String)

class SessionsScheduler @Inject()(sessionsRepository: SessionsRepository,
                                  usersRepository: UsersRepository,
                                  feedbackFormsRepository: FeedbackFormsRepository,
                                  configuration: Configuration,
                                  @Named("EmailManager") emailManager: ActorRef,
                                  dateTimeUtility: DateTimeUtility) extends Actor {

  lazy val fromEmail = configuration.getOptional[String]("play.mailer.user").getOrElse("support@knoldus.com")
  lazy val host = configuration.getOptional[String]("play.host.name").getOrElse("")
  val feedbackUrl = s"$host${routes.FeedbackFormsResponseController.getFeedbackFormsForToday().url}"

  var scheduledEmails: Map[String, Cancellable] = Map.empty

  override def preStart(): Unit = {
    val millis = dateTimeUtility.nowMillis
    val initialDelay = ((dateTimeUtility.endOfDayMillis + 61 * 1000) - millis).milliseconds
    Logger.info(s"Sessions scheduler will start after $initialDelay")

    val reminderTime: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.endOfDayMillis - millis).plusHours(10)
    val reminderInitialDelay = dateTimeUtility.toMillis(reminderTime).milliseconds

    self ! ScheduleFeedbackEmailsStartingToday(self, sessionsScheduledToday)
    self ! InitiateFeedbackEmailsStartingTomorrow(initialDelay, 1.day)

    self ! ScheduleFeedbackRemindersStartingToday(self, sessionsExpiringToday)
    self ! InitialFeedbackRemindersStartingTomorrow(reminderInitialDelay, 1.day)
  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive =
    initializingHandler orElse
      schedulingHandler orElse
      reconfiguringHandler orElse
      emailHandler orElse
      defaultHandler

  def initializingHandler: Receive = {
    case InitiateFeedbackEmailsStartingTomorrow(initialDelay, interval)           =>
      Logger.info(s"Initiating feedback emails schedulers to run everyday. These would be scheduled starting tomorrow.")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleFeedbackEmailsStartingTomorrow
      )(context.dispatcher)
    case InitialFeedbackRemindersStartingTomorrow(initialDelay, interval)         =>
      Logger.info(s"Initiating feedback reminder schedulers to run everyday. These would be scheduled starting tomorrow.")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleFeedbackRemindersStartingTomorrow
      )(context.dispatcher)
    case ScheduleFeedbackEmailsStartingToday(originalSender, eventualSessions)    =>
      Logger.info(s"Scheduling feedback form emails to be sent for all sessions scheduled for today. This would run only once.")
      val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = false)

      eventualScheduledSessions foreach { schedulers =>
        scheduledEmails = scheduledEmails ++ schedulers

        originalSender ! scheduledEmails.size
      }
    case ScheduleFeedbackRemindersStartingToday(originalSender, expiringSessions) =>
      Logger.info(s"Scheduling feedback form reminder email to be sent for expiring sessions. This would run only once for " +
        s"all sessions scheduled today.")
      val eventualExpiringSessionsReminder = scheduleEmails(expiringSessions, reminder = true)

      eventualExpiringSessionsReminder foreach { reminder =>
        scheduledEmails = scheduledEmails ++ reminder

        originalSender ! scheduledEmails.size
      }
  }

  def schedulingHandler: Receive = {
    case ScheduleFeedbackEmailsStartingTomorrow    =>
      Logger.info(s"Starting feedback emails schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsScheduledToday
      val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = false)
      eventualScheduledSessions.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case EventualScheduledEmails(scheduledMails)         =>
      scheduledEmails = scheduledEmails ++ scheduledMails
      Logger.info(s"All scheduled sessions in memory are ${scheduledEmails.keys}")
    case ScheduleFeedbackRemindersStartingTomorrow =>
      Logger.info(s"Starting feedback reminder schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsExpiringToday
      val eventualScheduledReminders = scheduleEmails(eventualSessions, reminder = true)
      eventualScheduledReminders.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case GetScheduledSessions                      =>
      Logger.info(s"Following sessions are scheduled ${scheduledEmails.keys}")
      sender ! ScheduledSessions(scheduledEmails.keys.toList)
    case ScheduleSession(sessionId)                =>
      Logger.info(s"Rescheduling session $sessionId")

      val eventualSessions = sessionsRepository.getById(sessionId) map (_.toList)
      val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = false)
      eventualScheduledSessions.map(schedule => EventualScheduledEmails(schedule)) pipeTo self
  }

  def reconfiguringHandler: Receive = {
    case RefreshSessionsSchedulers         =>
      Logger.info(s"Scheduled feedback emails before refreshing $scheduledEmails, now rescheduling at ${dateTimeUtility.localDateIST}")

      val cancelled = scheduledEmails.forall { case (_, cancellable) => cancellable.cancel }

      Logger.info(s"Scheduled feedback emails after refreshing $scheduledEmails")

      if (scheduledEmails.isEmpty || (scheduledEmails.nonEmpty && cancelled)) {
        val eventualSessions = sessionsRepository.sessionsForToday(SchedulingNext)
        val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = false)
        eventualScheduledSessions foreach { feedbackSchedulers => scheduledEmails = feedbackSchedulers }

        val expiringSession = sessionsRepository.sessionsForToday(ExpiringNext)
        val eventualScheduledReminders = scheduleEmails(expiringSession, reminder = true)
        eventualScheduledReminders foreach { feedbackSchedulers => scheduledEmails = feedbackSchedulers }

        sender ! ScheduledSessionsRefreshed
      } else {
        sender ! ScheduledSessionsNotRefreshed
      }
    case CancelScheduledSession(sessionId) =>
      Logger.info(s"Removing feedback emails scheduled for session $sessionId")

      scheduledEmails.get(sessionId).exists(_.cancel) match {
        case true  => Logger.info(s"Scheduled session $sessionId feedback email successfully cancelled")
        case false => Logger.info(s"Scheduled session $sessionId feedback email was already cancelled")
      }

      scheduledEmails = scheduledEmails - sessionId

      Logger.info(s"All scheduled feedback emails after removing $sessionId are ${scheduledEmails.keys}")

      sender ! scheduledEmails.get(sessionId).isEmpty
  }

  def emailHandler: Receive = {
    case SendEmail(sessions, reminder) if sessions.nonEmpty =>
      val recipients = usersRepository.getAllActiveEmails
      val emailInfo = sessions.map(session => EmailInfo(session.topic, session.email, new Date(session.date.value).toString))
      recipients collect {
        case emails if emails.nonEmpty =>
          if (reminder) {
            emailManager ! EmailActor.SendEmail(
              emails, fromEmail, "Feedback reminder", views.html.emails.reminder(emailInfo, feedbackUrl).toString)
            Logger.info(s"Reminder Email for session sent")
            scheduledEmails = scheduledEmails - dateTimeUtility.toLocalDate(sessions.head.date.value).toString
          } else {
            emailManager ! EmailActor.SendEmail(
              emails, fromEmail, s"${sessions.head.topic} Feedback Form", views.html.emails.feedback(emailInfo, feedbackUrl).toString)
            Logger.info(s"Feedback email for session ${sessions.head.session} sent, removing feedback form scheduler now")
            scheduledEmails = scheduledEmails - sessions.head._id.stringify
          }
      }
  }

  def defaultHandler: Receive = {
    case msg: Any =>
      Logger.error(s"Received a message $msg in Sessions Scheduler which cannot be handled")
  }

  def scheduleEmails(eventualSessions: Future[List[SessionInfo]], reminder: Boolean): Future[Map[String, Cancellable]] =
    eventualSessions collect { case sessions if sessions.nonEmpty =>
      if (reminder) {
        Map(dateTimeUtility.toLocalDate(sessions.head.date.value).toString ->
          scheduler.scheduleOnce(0.milliseconds, self, SendEmail(sessions, reminder)))
      } else {
        sessions.map { session =>
          val delay = (session.date.value - dateTimeUtility.nowMillis).milliseconds
          session._id.stringify -> scheduler.scheduleOnce(delay, self, SendEmail(List(session), reminder))
        }.toMap
      }
    }

  def sessionsScheduledToday: Future[List[SessionInfo]] = sessionsRepository.sessionsForToday(SchedulingNext)

  def sessionsExpiringToday: Future[List[SessionInfo]] = sessionsRepository.sessionsForToday(ExpiringNext)

}

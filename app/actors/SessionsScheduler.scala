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

  sealed trait EmailType
  case object Reminder extends EmailType
  case object Feedback extends EmailType
  case object Notification extends EmailType

  // messages used for getting/reconfiguring schedulers/scheduled-emails
  case object RefreshSessionsSchedulers
  case object GetScheduledSessions
  case object CancelAllScheduledEmails
  case class CancelScheduledSession(sessionId: String)
  case class ScheduleSession(sessionId: String)

  // messages used internally for starting session schedulers/emails
  case object ScheduleFeedbackEmailsStartingTomorrow
  case object ScheduleFeedbackRemindersStartingTomorrow
  case object ScheduleSessionNotificationStartingTomorrow

  private[actors] case class ScheduleFeedbackEmailsStartingToday(eventualSessions: Future[List[SessionInfo]])
  private[actors] case class InitiateFeedbackEmailsStartingTomorrow(initialDelay: FiniteDuration, interval: FiniteDuration)

  private[actors] case class ScheduleFeedbackRemindersStartingToday(eventualSessions: Future[List[SessionInfo]])
  private[actors] case class InitialFeedbackRemindersStartingTomorrow(initialDelay: FiniteDuration, interval: FiniteDuration)

  private[actors] case class ScheduleSessionNotificationsStartingToday(eventualSessions: Future[List[SessionInfo]])
  private[actors] case class InitialSessionNotificationsStartingTomorrow(initialDelay: FiniteDuration, interval: FiniteDuration)

  private[actors] case class EventualScheduledEmails(scheduledMails: Map[String, Cancellable])
  private[actors] case class SendEmail(session: List[SessionInfo], emailType: EmailType)

  // messages used for responding back with current schedulers state
  sealed trait SessionsSchedulerResponse
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
  lazy val host = configuration.getOptional[String]("knolx.url").getOrElse("localhost:9000")
  val feedbackUrl = s"$host${routes.FeedbackFormsResponseController.getFeedbackFormsForToday().url}"

  var scheduledEmails: Map[String, Cancellable] = Map.empty

  override def preStart(): Unit = {
    val millis = dateTimeUtility.nowMillis
    val initialDelay = ((dateTimeUtility.endOfDayMillis + 61 * 1000) - millis).milliseconds
    Logger.info(s"Sessions scheduler will start after $initialDelay")

    val reminderTime: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.endOfDayMillis - millis).plusHours(10)
    val reminderInitialDelay = dateTimeUtility.toMillis(reminderTime).milliseconds

    self ! ScheduleFeedbackEmailsStartingToday(sessionsScheduledToday)
    self ! InitiateFeedbackEmailsStartingTomorrow(initialDelay, 1.day)

/*    self ! ScheduleFeedbackRemindersStartingToday(sessionsExpiringToday)
    self ! InitialFeedbackRemindersStartingTomorrow(reminderInitialDelay, 1.day)

    self ! ScheduleSessionNotificationsStartingToday(sessionsScheduledToday)
    self ! InitialSessionNotificationsStartingTomorrow(initialDelay, 1.day)*/

  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive =
    initializingHandler orElse
      schedulingHandler orElse
      reconfiguringHandler orElse
      emailHandler orElse
      defaultHandler

  def initializingHandler: Receive = {
    case InitiateFeedbackEmailsStartingTomorrow(initialDelay, interval)      =>
      Logger.info(s"Initiating feedback emails schedulers to run everyday. These would be scheduled starting tomorrow.")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleFeedbackEmailsStartingTomorrow
      )(context.dispatcher)
    case InitialFeedbackRemindersStartingTomorrow(initialDelay, interval)    =>
      Logger.info(s"Initiating feedback reminder schedulers to run everyday. These would be scheduled starting tomorrow.")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleFeedbackRemindersStartingTomorrow
      )(context.dispatcher)
    case InitialSessionNotificationsStartingTomorrow(initialDelay, interval) =>
      Logger.info(s"Initiating session notification schedulers to run everyday. These would be scheduled starting tomorrow.")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleSessionNotificationStartingTomorrow
      )(context.dispatcher)
    case ScheduleFeedbackEmailsStartingToday(eventualSessions)               =>
      Logger.info(s"Scheduling feedback form emails to be sent for all sessions scheduled for today. This would run only once.")
      val eventualScheduledSessions = scheduleEmails(eventualSessions, Feedback)
      eventualScheduledSessions.map(schedulers => EventualScheduledEmails(schedulers)) pipeTo self
    case ScheduleFeedbackRemindersStartingToday(expiringSessions)            =>
      Logger.info(s"Scheduling feedback form reminder email to be sent for expiring sessions. This would run only once for " +
        s"all sessions scheduled today.")
      val eventualExpiringSessionsReminder = scheduleEmails(expiringSessions, Reminder)
      eventualExpiringSessionsReminder.map(schedule => EventualScheduledEmails(schedule)) pipeTo self
    case ScheduleSessionNotificationsStartingToday(eventualSessions)         =>
      Logger.info(s"Scheduling notification to be sent for today's sessions. This would run only once for " +
        s"all sessions scheduled today.")
      val eventualExpiringSessionsReminder = scheduleEmails(eventualSessions, Notification)
      eventualExpiringSessionsReminder.map(schedule => EventualScheduledEmails(schedule)) pipeTo self
  }

  def schedulingHandler: Receive = {
    case ScheduleFeedbackEmailsStartingTomorrow      =>
      Logger.info(s"Starting feedback emails schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsScheduledToday
      val eventualScheduledSessions = scheduleEmails(eventualSessions, Feedback)
      eventualScheduledSessions.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case EventualScheduledEmails(scheduledMails)     =>
      scheduledEmails = scheduledEmails ++ scheduledMails
      Logger.info(s"All scheduled sessions in memory are ${scheduledEmails.keys}")
    case ScheduleFeedbackRemindersStartingTomorrow   =>
      Logger.info(s"Starting feedback reminder schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsExpiringToday
      val eventualScheduledReminders = scheduleEmails(eventualSessions, Reminder)
      eventualScheduledReminders.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case ScheduleSessionNotificationStartingTomorrow =>
      Logger.info(s"Starting session Notification schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsScheduledToday
      val eventualScheduledReminders = scheduleEmails(eventualSessions, Notification)
      eventualScheduledReminders.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case GetScheduledSessions                        =>
      Logger.info(s"Following sessions are scheduled ${scheduledEmails.keys}")
      sender ! ScheduledSessions(scheduledEmails.keys.toList)
    case ScheduleSession(sessionId)                  =>
      Logger.info(s"Rescheduling session $sessionId")
      val eventualSessions = sessionsRepository.getById(sessionId) map (_.toList)
      val eventualScheduledSessions = scheduleEmails(eventualSessions, Feedback)
      eventualScheduledSessions.map(schedule => EventualScheduledEmails(schedule)) pipeTo self
  }

  def reconfiguringHandler: Receive = {
    case RefreshSessionsSchedulers         =>
      Logger.info(s"Scheduled sessions emails before refreshing $scheduledEmails, now rescheduling at ${dateTimeUtility.localDateIST}")

      if (scheduledEmails.nonEmpty) {
        scheduledEmails.foreach { case (scheduler, cancellable) =>
          cancellable.cancel
          scheduledEmails = scheduledEmails - scheduler
        }
      } else {
        Logger.info(s"No scheduled emails found")
      }
      val eventualSessions = sessionsRepository.sessionsForToday(SchedulingNext)
      val eventualScheduledSessions = scheduleEmails(eventualSessions, Feedback)
      eventualScheduledSessions.map(scheduler => EventualScheduledEmails(scheduler)) pipeTo self

/*      val expiringSession = sessionsRepository.sessionsForToday(ExpiringNext)
      val eventualScheduledReminders = scheduleEmails(expiringSession, Reminder)
      eventualScheduledReminders.map(scheduler => EventualScheduledEmails(scheduler)) pipeTo self

      val eventualScheduledNotifications = scheduleEmails(eventualSessions, Notification)
      eventualScheduledNotifications.map(scheduler => EventualScheduledEmails(scheduler)) pipeTo self*/

      Logger.info(s"Scheduled sessions emails after refreshing $scheduledEmails")

    case CancelScheduledSession(sessionId) =>
      Logger.info(s"Removing feedback emails scheduled for session $sessionId")

      scheduledEmails.get(sessionId).exists(_.cancel) match {
        case true  => Logger.info(s"Scheduled session $sessionId feedback email successfully cancelled")
        case false => Logger.info(s"Scheduled session $sessionId feedback email was already cancelled")
      }

      scheduledEmails = scheduledEmails - sessionId

      Logger.info(s"All scheduled feedback emails after removing $sessionId are ${scheduledEmails.keys}")

      sender ! scheduledEmails.get(sessionId).isEmpty
    case CancelAllScheduledEmails          =>
      Logger.info(s"Removing all notification, feedback and reminder emails scheduled")
      if (scheduledEmails.nonEmpty) {
        scheduledEmails.foreach { case (scheduler, cancellable) =>
          cancellable.cancel
          scheduledEmails = scheduledEmails - scheduler
        }
      } else {
        Logger.info(s"No scheduled emails found")
      }
      Logger.info(s"All scheduled  emails after removing all ${scheduledEmails.keys}")
      sender ! scheduledEmails.isEmpty
  }

  def emailHandler: Receive = {
    case SendEmail(sessions, emailType) if sessions.nonEmpty =>
      val recipients = usersRepository.getAllActiveEmails
      val emailInfo = sessions.map(session => EmailInfo(session.topic, session.email, new Date(session.date.value).toString))
      recipients collect {
        case emails if emails.nonEmpty =>
          emailType match {
            case Reminder     =>
              scheduledEmails = scheduledEmails - dateTimeUtility.toLocalDate(sessions.head.date.value).toString
              emailManager ! EmailActor.SendEmail(
                emails, fromEmail, "Feedback reminder", views.html.emails.reminder(emailInfo, feedbackUrl).toString)
              Logger.info(s"Reminder Email for session sent")
            case Feedback     =>
              scheduledEmails = scheduledEmails - sessions.head._id.stringify
              emailManager ! EmailActor.SendEmail(
                emails, fromEmail, s"${sessions.head.topic} Feedback Form", views.html.emails.feedback(emailInfo, feedbackUrl).toString)
              Logger.info(s"Feedback email for session ${sessions.head.session} sent")
            case Notification =>
              scheduledEmails = scheduledEmails - s"notify${dateTimeUtility.toLocalDate(sessions.head.date.value).toString}"
              emailManager ! EmailActor.SendEmail(
                emails, fromEmail, "Knolx/Meetup Sessions", views.html.emails.notification(emailInfo).toString)
              Logger.info(s"Notification Email for session sent")
          }
      }
  }

  def defaultHandler: Receive = {
    case msg: Any =>
      Logger.error(s"Received a message $msg in Sessions Scheduler which cannot be handled")
  }

  def scheduleEmails(eventualSessions: Future[List[SessionInfo]], emailType: EmailType): Future[Map[String, Cancellable]] =
    eventualSessions collect { case sessions if sessions.nonEmpty =>
      emailType match {
        case Reminder     =>
          //with 10 min delay
          Map(dateTimeUtility.toLocalDate(sessions.head.date.value).toString ->
            scheduler.scheduleOnce(600000.milliseconds, self, SendEmail(sessions, Reminder)))
        case Feedback     => sessions.map { session =>
          val delay = (session.date.value - dateTimeUtility.nowMillis).milliseconds
          session._id.stringify -> scheduler.scheduleOnce(delay, self, SendEmail(List(session), Feedback))
        }.toMap
        case Notification =>
          //with 10 min delay
          Map(s"notify${dateTimeUtility.toLocalDate(sessions.head.date.value).toString}" ->
            scheduler.scheduleOnce(600000.milliseconds, self, SendEmail(sessions, Notification)))
      }
    }

  def sessionsScheduledToday: Future[List[SessionInfo]] = sessionsRepository.sessionsForToday(SchedulingNext)

  def sessionsExpiringToday: Future[List[SessionInfo]] = sessionsRepository.sessionsForToday(ExpiringNext)

}

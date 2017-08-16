package actors

import java.time.LocalDateTime
import javax.inject.{Inject, Named}

import actors.SessionsScheduler._
import akka.actor.{Actor, ActorRef, Cancellable, Scheduler}
import controllers.EmailComposer._
import models.SessionJsonFormats.{ExpiringNext, Scheduled, SchedulingNext}
import models.{FeedbackFormsRepository, SessionInfo, SessionsRepository, UsersRepository}
import play.api.{Configuration, Logger}
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
  private[actors] case class ScheduleRemainder(originalSender: ActorRef)
  private[actors] case class StartSessionsScheduler(initialDelay: FiniteDuration, interval: FiniteDuration)
  private[actors] case class StartFeedbackReminderMailScheduler(initialDelay: FiniteDuration, interval: FiniteDuration)
  private[actors] case class SendEmail(session: List[SessionInfo], reminder: Boolean)
  private[actors] case class SendReminderMailForToday(originalSender: ActorRef, eventualSessions: Future[List[SessionInfo]])

  sealed trait SessionsSchedulerResponse
  case object ScheduledSessionsRefreshed extends SessionsSchedulerResponse
  case object ScheduledSessionsNotRefreshed extends SessionsSchedulerResponse
  case class ScheduledSessions(sessionIds: List[String]) extends SessionsSchedulerResponse

}

class SessionsScheduler @Inject()(sessionsRepository: SessionsRepository,
                                  usersRepository: UsersRepository,
                                  feedbackFormsRepository: FeedbackFormsRepository,
                                  configuration: Configuration,
                                  @Named("EmailManager") emailManager: ActorRef,
                                  dateTimeUtility: DateTimeUtility) extends Actor {

  val fromEmail: String = configuration.getOptional[String]("play.mailer.user").getOrElse("support@knoldus.com")
  var scheduledEmails: Map[String, Cancellable] = Map.empty

  override def preStart(): Unit = {
    val millis = dateTimeUtility.nowMillis
    val initialDelay = ((dateTimeUtility.endOfDayMillis + 61 * 1000) - millis).milliseconds
    Logger.info(s"Sessions scheduler will start after $initialDelay")

    val reminderTime: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.endOfDayMillis - millis).plusHours(10)
    val reminderInitialDelay = dateTimeUtility.toMillis(reminderTime).milliseconds

    self ! ScheduleSessionsForToday(self, sessionsScheduledToday)
    self ! StartSessionsScheduler(initialDelay, 1.day)

    self ! SendReminderMailForToday(self, sessionsExpiringToday)
    self ! StartFeedbackReminderMailScheduler(reminderInitialDelay, 1.day)
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
    case StartFeedbackReminderMailScheduler(initialDelay, interval) =>
      Logger.info(s"Configuring sessions reminder scheduler to run every day")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleRemainder
      )(context.dispatcher)
    case ScheduleSessionsForToday(originalSender, eventualSessions) =>
      Logger.info(s"Scheduling today's sessions")
      val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = false)

      eventualScheduledSessions foreach { schedulers =>
        scheduledEmails = scheduledEmails ++ schedulers

        originalSender ! scheduledEmails.size
      }
    case SendReminderMailForToday(originalSender, expiringSessions) =>
      Logger.info(s"Scheduling today's reminders")
      val eventualExpiringSessionsReminder = scheduleEmails(expiringSessions, reminder = true)

      eventualExpiringSessionsReminder foreach { reminder =>
        scheduledEmails = scheduledEmails ++ reminder

        originalSender ! scheduledEmails.size
      }
  }

  def schedulingHandler: Receive = {
    case ScheduleSessions(originalSender)  =>
      Logger.info(s"Starting schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsScheduledToday
      val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = false)

      eventualScheduledSessions foreach { schedulers =>
        scheduledEmails = scheduledEmails ++ schedulers

        originalSender ! scheduledEmails.size
      }
    case ScheduleRemainder(originalSender) =>
      Logger.info(s"Starting schedulers for Knolx session reminder on ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsExpiringToday
      val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = true)

      eventualScheduledSessions foreach { reminder =>
        scheduledEmails = scheduledEmails ++ reminder

        originalSender ! scheduledEmails.size
      }
    case GetScheduledSessions              =>
      Logger.info(s"Following sessions are scheduled ${scheduledEmails.keys}")

      sender ! ScheduledSessions(scheduledEmails.keys.toList)
    case ScheduleSession(sessionId)        =>
      val originalSender = sender

      Logger.info(s"Rescheduling session $sessionId")

      val eventualSessions = sessionsRepository.getById(sessionId) map (_.toList)
      val eventualScheduledSessions = scheduleEmails(eventualSessions, reminder = false)

      eventualScheduledSessions foreach { schedulers =>
        scheduledEmails = scheduledEmails ++ schedulers

        Logger.info(s"All scheduled sessions in memory after adding $sessionId are ${scheduledEmails.keys}")

        originalSender ! scheduledEmails.get(sessionId).isDefined
      }
  }

  def reconfiguringHandler: Receive = {
    case RefreshSessionsSchedulers         =>
      Logger.info(s"Scheduled sessions in memory before refreshing $scheduledEmails")
      Logger.info(s"Refreshing schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val cancelled = scheduledEmails.forall { case (_, cancellable) => cancellable.cancel }

      Logger.info(s"Scheduled sessions in memory after refreshing $scheduledEmails")
      if (scheduledEmails.isEmpty || (scheduledEmails.nonEmpty && cancelled)) {
        val eventualSessions = sessionsRepository.sessionsForToday(Scheduled)
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
      Logger.info(s"Removing scheduler for session $sessionId")

      scheduledEmails.get(sessionId).exists(_.cancel) match {
        case true  => Logger.info(s"Scheduled session $sessionId successfully cancelled")
        case false => Logger.info(s"Scheduled session $sessionId was already cancelled")
      }

      scheduledEmails = scheduledEmails - sessionId

      Logger.info(s"All scheduled sessions in memory after removing $sessionId are ${scheduledEmails.keys}")

      sender ! scheduledEmails.get(sessionId).isEmpty
  }

  def emailHandler: Receive = {
    case SendEmail(sessions, reminder) if sessions.nonEmpty =>
      val recipients = usersRepository.getAllActiveEmails

      recipients collect {
        case emails if emails.nonEmpty =>
          if (reminder) {
            emailManager ! EmailActor.SendEmail(List("platoonhead@gmail.com"), fromEmail, "Feedback reminder", reminderMailBody(sessions))
            Logger.info(s"Reminder Email for session sent")
            scheduledEmails = scheduledEmails - dateTimeUtility.toLocalDate(sessions.head.date.value).toString
          } else {
            emailManager ! EmailActor.SendEmail(List("platoonhead@gmail.com"), fromEmail, s"${sessions.head.topic} Feedback Form", feedbackMailBody(sessions))
            Logger.info(s"Email for session ${sessions.head.session} sent, removing feedback form scheduler now")
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

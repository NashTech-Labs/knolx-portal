package actors

import java.time.LocalDateTime
import java.util.Date
import javax.inject.{Inject, Named}

import actors.SessionsScheduler._
import akka.actor.{Actor, ActorRef, Cancellable, Scheduler}
import controllers.routes
import models.SessionJsonFormats.{ExpiringNextNotReminded, SchedulingNext, SchedulingNextUnNotified, SessionState}
import models.{FeedbackFormsRepository, FeedbackFormsResponseRepository, SessionInfo, SessionsRepository, UsersRepository}
import play.api.{Configuration, Logger}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{FiniteDuration, _}
import akka.pattern.pipe

object SessionsScheduler {

  sealed trait EmailType

  sealed trait EmailOnce

  case object Reminder extends EmailType with EmailOnce

  case object Notification extends EmailType with EmailOnce

  case object Feedback extends EmailType

  // messages used for getting/reconfiguring schedulers/scheduled-emails
  case object RefreshSessionsSchedulers

  case object GetScheduledSessions

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

case class DefaultersPerSession(email: List[String], emailInfo: EmailInfo)

case class EmailInfo(topic: String, presenter: String, date: String)

class SessionsScheduler @Inject()(sessionsRepository: SessionsRepository,
                                  usersRepository: UsersRepository,
                                  feedbackFormsRepository: FeedbackFormsRepository,
                                  feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                  configuration: Configuration,
                                  @Named("EmailManager") emailManager: ActorRef,
                                  dateTimeUtility: DateTimeUtility) extends Actor {

  lazy val fromEmail: String = configuration.getOptional[String]("play.mailer.user").getOrElse("support@knoldus.com")
  lazy val host: String = configuration.getOptional[String]("knolx.url").getOrElse("localhost:9000")
  val feedbackUrl = s"$host${routes.FeedbackFormsResponseController.getFeedbackFormsForToday().url}"

  var scheduledEmails: Map[String, Cancellable] = Map.empty

  override def preStart(): Unit = {
    val millis = dateTimeUtility.nowMillis
    val initialDelay = ((dateTimeUtility.endOfDayMillis + 61 * 1000) - millis).milliseconds
    Logger.info(s"Sessions scheduler will start after $initialDelay")

    val tenHrsDelay: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.endOfDayMillis - millis).plusHours(10)
    val tenHrsDelayMillis = dateTimeUtility.toMillis(tenHrsDelay).milliseconds

    self ! ScheduleFeedbackEmailsStartingToday(sessionsForToday(SchedulingNext))
    self ! InitiateFeedbackEmailsStartingTomorrow(initialDelay, 1.day)

    self ! ScheduleFeedbackRemindersStartingToday(sessionsForToday(ExpiringNextNotReminded))
    self ! InitialFeedbackRemindersStartingTomorrow(tenHrsDelayMillis, 1.day)

    self ! ScheduleSessionNotificationsStartingToday(sessionsForToday(SchedulingNextUnNotified))
    self ! InitialSessionNotificationsStartingTomorrow(tenHrsDelayMillis, 1.day)
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
      Logger.info("-----------------Sessions received are = " + expiringSessions)
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
      val eventualSessions = sessionsForToday(SchedulingNext)
      val eventualScheduledSessions = scheduleEmails(eventualSessions, Feedback)
      eventualScheduledSessions.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case ScheduleFeedbackRemindersStartingTomorrow   =>
      Logger.info(s"Starting feedback reminder schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsForToday(ExpiringNextNotReminded)
      val eventualScheduledReminders = scheduleEmails(eventualSessions, Reminder)
      eventualScheduledReminders.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case ScheduleSessionNotificationStartingTomorrow =>
      Logger.info(s"Starting session Notification schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      val eventualSessions = sessionsForToday(SchedulingNextUnNotified)
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
    case EventualScheduledEmails(scheduledMails)     =>
      scheduledEmails = scheduledEmails ++ scheduledMails
      Logger.info(s"All scheduled sessions in memory are ${scheduledEmails.keys}")
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

      val expiringSession = sessionsRepository.sessionsForToday(ExpiringNextNotReminded)
      val eventualScheduledReminders = scheduleEmails(expiringSession, Reminder)
      eventualScheduledReminders.map(scheduler => EventualScheduledEmails(scheduler)) pipeTo self

      val eventualNotifications = sessionsRepository.sessionsForToday(SchedulingNextUnNotified)
      val eventualScheduledNotifications = scheduleEmails(eventualNotifications, Notification)
      eventualScheduledNotifications.map(scheduler => EventualScheduledEmails(scheduler)) pipeTo self

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
  }

  def emailHandler: Receive = {
    case SendEmail(sessions, emailType) if sessions.nonEmpty =>
      val recipients = usersRepository.getAllActiveEmails
      val emailInfo = sessions.map(session => EmailInfo(session.topic, session.email, new Date(session.date.value).toString))
      recipients collect {
        case emails if emails.nonEmpty =>
          emailType match {
            case Reminder     =>
              Logger.info("--------------In send email reminder, the session are = " + sessions)
              reminderEmailHandler(sessions, emailInfo, emails)
            case Feedback     => feedbackEmailHandler(sessions, emailInfo, emails)
            case Notification => notificationEmailHandler(sessions, emailInfo, emails)
          }
      }
  }

  def defaultHandler: Receive = {
    case msg: Any =>
      Logger.error(s"Received a message $msg in Sessions Scheduler which cannot be handled")
  }

  def reminderEmailHandler(sessions: List[SessionInfo], emailInfo: List[EmailInfo], emails: List[String]): Unit = {
    Logger.info("Sessions List : " + sessions)
    val key = dateTimeUtility.toLocalDate(sessions.head.date.value).toString
    val presenterEmails = sessions.map(_.email)
    val emailsExceptPresenter = emails diff presenterEmails
    /*Logger.info("List of emails!!!!!!!!!!!!!!!! " + Await.result(
      feedbackFormsResponseRepository.getAllResponseEmailsPerSession("59c8aad97900006001a22246"),Duration.Inf)    )

    sessions.map{ session =>

      val reminder = Map( session._id -> Await.result(feedbackFormsResponseRepository.getAllResponseEmailsPerSession(session._id.stringify),Duration.Inf))
    }*/

    val x: List[Future[List[(String, EmailInfo)]]] = sessions.map { session =>
      feedbackFormsResponseRepository.getAllResponseEmailsPerSession(session._id.stringify).map {
        listOfEmails =>
          val ak1 = emailsExceptPresenter diff listOfEmails
          Logger.info("List of emails : " + ak1)
          ak1.map {
            defaulter =>
              (defaulter, emailInfo.filter(f => session.email == f.presenter).head)
          }
        //DefaultersPerSession(emails diff listOfEmails, emailInfo.filter(f => session.email == f.presenter).head)
        /*emailManager ! EmailActor.SendEmail(
          defaulters, fromEmail, "Feedback reminder", views.html.emails.reminder(emailInfo, feedbackUrl).toString()*/
      }
    }

    val y = Future.sequence(x)

    val z = y.map(_.flatten)
    val aa: Future[Map[String, List[EmailInfo]]] = z.map {
      _.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
    }

    aa.map { op =>
      op.foreach { case (k, v) =>
        Logger.info("List of emails: " + k + "->" + v)
        emailManager ! EmailActor.SendEmail(
          List(k), fromEmail, "Feedback reminder", views.html.emails.reminder(v, feedbackUrl).toString()
        )
      }
    }
    scheduledEmails = scheduledEmails - key


    /*emailManager ! EmailActor.SendEmail(
      emailsExceptPresenter, fromEmail, "Feedback reminder", views.html.emails.reminder(emailInfo, feedbackUrl).toString)
    Logger.info(s"Reminder Email for sessions expiring on $key sent")*/

    sessions.map {
      session =>
        Logger.info(s"Setting reminder field true after sending reminder email for session ${session._id.stringify}")
        sessionsRepository.upsertRecord(session, Reminder)
    }.map {
      _.map { result =>
        if (result.ok) {
          Logger.info(s"Reminder field is set true after reminder email sent for session ${sessions.map(_._id.stringify)} on $key")
        } else {
          Logger.info(s"Something went wrong while setting reminder field after reminder email sent for session" +
            s" ${sessions.map(_._id.stringify)} on $key")
        }
      }
    }
    reminderEmailHandlerForPresenter(sessions, emailInfo, presenterEmails)
  }

  def reminderEmailHandlerForPresenter(sessions: List[SessionInfo], emailInfo: List[EmailInfo], presenterEmails: List[String]): Unit = {
    presenterEmails foreach { presenterEmail =>
      val presenterOtherTopic = emailInfo.filterNot(_.presenter == presenterEmail)
      if (presenterOtherTopic.nonEmpty) {
        emailManager ! EmailActor.SendEmail(
          List(presenterEmail), fromEmail, "Feedback Reminder", views.html.emails.reminder(presenterOtherTopic, feedbackUrl).toString)
      }
      else {
        Logger.error("No other session for Presenter to remind for feedback")
      }
    }
  }


  def notificationEmailHandler(sessions: List[SessionInfo], emailInfo: List[EmailInfo], emails: List[String]): Unit = {
    val key = s"notify${dateTimeUtility.toLocalDate(sessions.head.date.value).toString}"

    scheduledEmails = scheduledEmails - key

    emailManager ! EmailActor.SendEmail(
      emails, fromEmail, "Knolx/Meetup Sessions", views.html.emails.notification(emailInfo).toString)

    Logger.info(s"Notification Email for sessions held on $key sent")

    sessions.map { session =>
      Logger.info(s"Setting notification field true after sending reminder email for session ${session._id.stringify}")
      sessionsRepository.upsertRecord(session, Notification)
    }.map {
      _.map { result =>
        if (result.ok) {
          Logger.info(s"Notification field is set true after notification email sent for session ${sessions.map(_._id.stringify)} on $key")
        } else {
          Logger.info(s"Something went wrong while setting Notification field after notification email sent " +
            s"for session ${sessions.map(_._id.stringify)} on $key")
        }
      }
    }
  }

  def feedbackEmailHandler(sessions: List[SessionInfo], emailInfo: List[EmailInfo], emails: List[String]): Unit = {
    scheduledEmails = scheduledEmails - sessions.head._id.stringify
    val emailsExceptPresenter = emails.filterNot(_.equals(sessions.head.email))

    emailManager ! EmailActor.SendEmail(
      emailsExceptPresenter, fromEmail, s"${sessions.head.topic} Feedback Form", views.html.emails.feedback(emailInfo, feedbackUrl).toString)

    Logger.info(s"Feedback email for session ${sessions.head.session} sent")
  }

  def scheduleEmails(eventualSessions: Future[List[SessionInfo]], emailType: EmailType): Future[Map[String, Cancellable]] = {
    println("|||||"+Await.result(eventualSessions,Duration.Inf))
    eventualSessions collect { case sessions if sessions.nonEmpty =>
      emailType match {
        case Reminder     =>
          Logger.info("-------------In reminder, the sessions are = " + sessions)
          Map(dateTimeUtility.toLocalDate(sessions.head.date.value).toString ->
            scheduler.scheduleOnce(Duration.Zero, self, SendEmail(sessions, Reminder)))
        case Feedback     => sessions.map { session =>
          val delay = (session.date.value - dateTimeUtility.nowMillis).milliseconds
          session._id.stringify -> scheduler.scheduleOnce(delay, self, SendEmail(List(session), Feedback))
        }.toMap
        case Notification =>
          Map(s"notify${dateTimeUtility.toLocalDate(sessions.head.date.value).toString}" ->
            scheduler.scheduleOnce(Duration.Zero, self, SendEmail(sessions, Notification)))
      }
    }
  }

  def sessionsForToday(sessionState: SessionState): Future[List[SessionInfo]] = {
    Logger.info("------------------Sessions for today = " + sessionsRepository.sessionsForToday(sessionState))
    sessionsRepository.sessionsForToday(sessionState)
  }

}

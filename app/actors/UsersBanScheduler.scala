package actors

import java.time.LocalDateTime
import java.util.Date
import javax.inject.{Inject, Named}

import actors.UsersBanScheduler._
import akka.actor.{Actor, ActorRef, Cancellable, Scheduler}
import models.SessionJsonFormats.ExpiringNext
import models._
import play.api.{Configuration, Logger}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import akka.pattern.pipe

object UsersBanScheduler {

  case class EmailBodyInfo(topic: String, presenter: String, date: String)
  case class EmailContent(to: String, body: List[EmailBodyInfo])

  // messages used internally for starting session schedulers/emails
  private[actors] case object ScheduleBanEmails
  private[actors] case class InitiateBanEmails(initialDelay: FiniteDuration, interval: FiniteDuration)
  private[actors] case class SendEmail(session: EmailContent)
  private[actors] case class EventualScheduledEmails(scheduledMails: Map[String, Cancellable])

  // messages used for getting/reconfiguring schedulers/scheduled-emails
  case object RefreshSessionsBanSchedulers
  case object GetScheduledBannedUsers

}

class UsersBanScheduler @Inject()(sessionsRepository: SessionsRepository,
                                  usersRepository: UsersRepository,
                                  feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                  configuration: Configuration,
                                  @Named("EmailManager") emailManager: ActorRef,
                                  dateTimeUtility: DateTimeUtility) extends Actor {

  lazy val fromEmail: String = configuration.getOptional[String]("play.mailer.user").getOrElse("support@knoldus.com")
  var scheduledBanEmails: Map[String, Cancellable] = Map.empty

  override def preStart(): Unit = {
    val executionDelay: LocalDateTime = dateTimeUtility.toLocalDateTime(dateTimeUtility.endOfDayMillis - dateTimeUtility.nowMillis).minusMinutes(30)
    val initialDelay: FiniteDuration = dateTimeUtility.toMillis(executionDelay).milliseconds

    self ! InitiateBanEmails(initialDelay, 1.day)

    Logger.info(s"Ban scheduler will start after initial delay of " + initialDelay)
  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive = initializingHandler orElse
    schedulingHandler orElse
    emailHandler orElse
    defaultHandler

  def initializingHandler: Receive = {
    case InitiateBanEmails(delay, interval) =>
      Logger.info(s"Initiating ban emails schedulers to run everyday. These would be scheduled starting end of the day")

      scheduler.schedule(
        initialDelay = delay,
        interval = interval,
        receiver = self,
        message = ScheduleBanEmails
      )(context.dispatcher)
  }

  def schedulingHandler: Receive = {
    case ScheduleBanEmails                       =>
      val eventuallyExpiringSession = sessionsExpiringToday
      val eventualScheduledBanEmails = scheduleBanEmails(eventuallyExpiringSession)
      Logger.info(s"Starting ban emails schedulers to run everyday. Started at ${dateTimeUtility.localDateIST}")
      eventualScheduledBanEmails.map(scheduledMails => EventualScheduledEmails(scheduledMails)) pipeTo self
    case EventualScheduledEmails(scheduledMails) =>
      scheduledBanEmails = scheduledBanEmails ++ scheduledMails
      Logger.info(s"All scheduled sessions in memory are ${scheduledBanEmails.keys}")
    case GetScheduledBannedUsers                 =>
      Logger.info(s"Following bans are scheduled ${scheduledBanEmails.keys}")
      sender ! scheduledBanEmails.keys.toList
  }

  def emailHandler: Receive = {
    case SendEmail(emailContent) =>
      scheduledBanEmails = scheduledBanEmails - emailContent.to
      usersRepository.ban(emailContent.to).map(_.ok).collect { case banned if banned =>
        emailManager ! EmailActor.SendEmail(
          List(emailContent.to), fromEmail, s"BAN FROM KNOLX", views.html.emails.ban(emailContent.body).toString)
      }
  }

  def getBanInfo(sessions: List[SessionInfo], emails: List[String]): Future[List[EmailContent]] = {
    Future.sequence(sessions.map { session =>
      Logger.info(s"Collecting response emails for session id ${session._id.stringify}")
      feedbackFormsResponseRepository.getAllResponseEmailsPerSession(session._id.stringify)
        .map { responseEmails =>
          val bannedBySession = emails.filter(_ != session.email) diff responseEmails
          Map(EmailBodyInfo(session.topic, session.email, new Date(session.date.value).toString) -> bannedBySession)
        }
    }).map { banned =>
      val banInfo = banned.flatten.toMap
      val bannedEmails = banInfo.values.toList.flatten.distinct
      bannedEmails map { email =>
        val sessionBannedOn = banInfo.keys.collect { case sessionTopic if banInfo(sessionTopic).contains(email) => sessionTopic }.toList
        EmailContent(email, sessionBannedOn)
      }
    }
  }

  def defaultHandler: Receive = {
    case msg: Any =>
      Logger.error(s"Received a message $msg in Ban Scheduler which cannot be handled")
  }

  def scheduleBanEmails(eventualExpiringSessions: Future[List[SessionInfo]]): Future[Map[String, Cancellable]] = {
    eventualExpiringSessions.flatMap { sessions =>
      val recipients = usersRepository.getAllActiveEmails
      recipients.flatMap { emails =>
        getBanInfo(sessions, emails)
          .map {
            _.map { emailContent =>
              emailContent.to -> scheduler.scheduleOnce(Duration.Zero, self, SendEmail(emailContent))
            }.toMap
          }
      }
    }
  }

  def sessionsExpiringToday: Future[List[SessionInfo]] = sessionsRepository.sessionsForToday(ExpiringNext)
}

package schedulers

import java.util.Date
import javax.inject.Inject

import akka.actor.{Actor, ActorRef, Cancellable, Scheduler}
import models.{FeedbackForm, FeedbackFormsRepository, SessionInfo, SessionsRepository}
import play.api.Logger
import play.api.libs.mailer.{Email, MailerClient}
import schedulers.FeedbackFormsScheduler._
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}

object FeedbackFormsScheduler {

  case object RefreshFeedbackFormSchedulers

  case object GetScheduledFeedbackForms

  case class RemoveFeedbackFormScheduler(sessionId: String)

  private[schedulers] case class ScheduleFeedbackFormsForToday(originalSender: ActorRef, eventualSessions: Future[List[SessionInfo]])

  private[schedulers] case class ScheduleFeedbackForms(originalSender: ActorRef)

  private[schedulers] case class StartFeedbackFormsScheduler(initialDelay: FiniteDuration, interval: FiniteDuration)

  private[schedulers] case class SendFeedbackForm(session: SessionInfo, feedbackForm: FeedbackForm)

  sealed trait FeedbackFormSchedulerResponses

  case object Restarted extends FeedbackFormSchedulerResponses

  case object NotRestarted extends FeedbackFormSchedulerResponses

  case class ScheduledFeedbackForms(feedbackForms: List[String])

  val ToEmail = "sidharth@knoldus.com"
  val FromEmail = "sidharth@knoldus.com"
}

class FeedbackFormsScheduler @Inject()(sessionsRepository: SessionsRepository,
                                       feedbackFormsRepository: FeedbackFormsRepository,
                                       mailerClient: MailerClient,
                                       dateTimeUtility: DateTimeUtility) extends Actor {

  var scheduledFeedbackForms: Map[String, Cancellable] = Map.empty

  override def preStart(): Unit = {
    val millis = dateTimeUtility.nowMillis
    val initialDelay = ((dateTimeUtility.endOfDayMillis + 61 * 1000) - millis).milliseconds
    Logger.info(s"Feedback forms scheduler will start after $initialDelay")

    // starts feedback form schedulers for sessions starting next day
    self ! StartFeedbackFormsScheduler(initialDelay, 1.day)

    val sessionsScheduledToday = sessionsRepository.sessionsScheduledToday map { sessions =>
      sessions collect { case session
        if new Date(session.date.value).after(new Date(millis)) => session
      }
    }

    // starts feedback form schedulers for today's sessions
    self ! ScheduleFeedbackFormsForToday(self, sessionsScheduledToday)
  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive =
    initializationHandler orElse
      schedulingHandler orElse
      reconfigurationHandler orElse
      emailHandler

  def initializationHandler: PartialFunction[Any, Unit] = {
    case StartFeedbackFormsScheduler(initialDelay, interval)             =>
      Logger.info(s"Configuring feedback forms scheduler to run every day")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleFeedbackForms
      )(context.dispatcher)
    case ScheduleFeedbackFormsForToday(originalSender, eventualSessions) =>
      Logger.info(s"Configuring feedback form schedulers for today's sessions")
      val feedbackFormsSchedulersFuture = scheduleFeedbackForms(eventualSessions)

      feedbackFormsSchedulersFuture foreach { feedbackSchedulers =>
        scheduledFeedbackForms = scheduledFeedbackForms ++ feedbackSchedulers

        originalSender ! scheduledFeedbackForms.size
      }
  }

  def schedulingHandler: PartialFunction[Any, Unit] = {
    case ScheduleFeedbackForms(originalSender) =>
      Logger.info(s"Starting schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val eventualSessions =
        sessionsRepository.sessionsScheduledToday map { sessions =>
          sessions collect { case session
            if new Date(session.date.value).after(new Date(dateTimeUtility.nowMillis)) => session
          }
        }
      val feedbackFormsSchedulersFuture = scheduleFeedbackForms(eventualSessions)

      feedbackFormsSchedulersFuture foreach { feedbackSchedulers =>
        scheduledFeedbackForms = scheduledFeedbackForms ++ feedbackSchedulers

        originalSender ! scheduledFeedbackForms.size
      }
    case GetScheduledFeedbackForms             =>
      Logger.info(s"Following feedback forms are scheduled ${scheduledFeedbackForms.keys}")

      sender ! ScheduledFeedbackForms(scheduledFeedbackForms.keys.toList)
  }

  def reconfigurationHandler: PartialFunction[Any, Unit] = {
    case RefreshFeedbackFormSchedulers          =>
      Logger.info(s"Feedback forms in memory before refreshing $scheduledFeedbackForms")
      Logger.info(s"Refreshing schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val cancelled = scheduledFeedbackForms.forall { case (_, cancellable) => cancellable.cancel }

      if (scheduledFeedbackForms.isEmpty || (scheduledFeedbackForms.nonEmpty && cancelled)) {
        val eventualSessionInfoes = sessionsRepository.sessionsScheduledToday
        val eventualFormSchedulers = scheduleFeedbackForms(eventualSessionInfoes)
        eventualFormSchedulers foreach { feedbackSchedulers => scheduledFeedbackForms = feedbackSchedulers }

        sender ! Restarted
      } else {
        sender ! NotRestarted
      }
    case RemoveFeedbackFormScheduler(sessionId) =>
      Logger.info(s"Removing feedback form scheduler for session $sessionId")

      scheduledFeedbackForms.get(sessionId).exists(_.cancel) match {
        case true  => Logger.info(s"Feedback form scheduler for session $sessionId successfully cancelled")
        case false => Logger.info(s"Feedback form scheduler for session $sessionId was already cancelled")
      }

      scheduledFeedbackForms = scheduledFeedbackForms - sessionId

      sender ! scheduledFeedbackForms.size
  }

  def emailHandler: PartialFunction[Any, Unit] = {
    case SendFeedbackForm(session, feedbackForm) =>
      val email =
        Email(subject = s"${session.topic} Feedback Form",
          from = FromEmail,
          to = List(ToEmail),
          bodyHtml = None,
          bodyText = Some("Hello World"), replyTo = None)

      val emailSent = mailerClient.send(email)

      self ! RemoveFeedbackFormScheduler(session._id.stringify)

      Logger.info(s"Email for session ${session.session} sent result $emailSent")
  }

  private def scheduleFeedbackForms(eventualSessions: Future[List[SessionInfo]]): Future[Map[String, Cancellable]] = {
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
              Some(session._id.stringify -> scheduler.scheduleOnce(delay, self, SendFeedbackForm(session, feedbackForm)))
            }
          }
        }
      }
    } map (_.flatten.toMap)
  }

}

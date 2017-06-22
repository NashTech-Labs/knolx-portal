package schedulers

import java.util.Date
import javax.inject.Inject

import akka.actor.{ActorRef, Actor, Cancellable, Scheduler}
import models.{FeedbackForm, FeedbackFormsRepository, SessionInfo, SessionsRepository}
import play.api.Logger
import play.api.libs.mailer.{Email, MailerClient}
import schedulers.FeedbackFormsScheduler._
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{FiniteDuration, _}

object FeedbackFormsScheduler {

  private[schedulers] case class ScheduleFeedbackFormsForToday(originalSender: ActorRef, sessionsFuture: Future[List[SessionInfo]])

  private[schedulers] case class ScheduleFeedbackForms(originalSender: ActorRef)

  private[schedulers] case class StartFeedbackFormsScheduler(initialDelay: FiniteDuration, interval: FiniteDuration)

  private[schedulers] case class SendFeedbackForm(session: SessionInfo, feedbackForm: FeedbackForm)

  case object RefreshFeedbackFormSchedulers

  sealed trait FeedbackFormSchedulerResponses

  case object Restarted extends FeedbackFormSchedulerResponses

  case object NotRestarted extends FeedbackFormSchedulerResponses

  val ToEmail = "sidharth@knoldus.com"
  val FromEmail = "sidharth@knoldus.com"
}

class FeedbackFormsScheduler @Inject()(sessionsRepository: SessionsRepository,
                                       feedbackFormsRepository: FeedbackFormsRepository,
                                       mailerClient: MailerClient,
                                       dateTimeUtility: DateTimeUtility) extends Actor {

  var feedbackFormsSchedulers: List[Cancellable] = List.empty

  override def preStart(): Unit = {
    val millis = dateTimeUtility.nowMillis
    val initialDelay = ((dateTimeUtility.endOfDayMillis + 61 * 1000) - millis).milliseconds
    Logger.info(s"Feedback forms scheduler will start after $initialDelay")

    self ! StartFeedbackFormsScheduler(initialDelay, 1.day) // schedules starting next day

    val sessionsScheduledToday = sessionsRepository.sessionsScheduledToday map { sessions =>
      sessions collect { case session
        if new Date(session.date.value).after(new Date(millis)) => session
      }
    }

    self ! ScheduleFeedbackFormsForToday(self, sessionsScheduledToday) // schedules for today
  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive =
    initializationHandler orElse
      reconfigurationHandler orElse
      emailHandler

  def initializationHandler: PartialFunction[Any, Unit] = {
    case StartFeedbackFormsScheduler(initialDelay, interval)           =>
      Logger.info(s"Configuring feedback forms scheduler to run every day")

      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = ScheduleFeedbackForms
      )(context.dispatcher)
    case ScheduleFeedbackFormsForToday(originalSender, sessionsFuture) =>
      Logger.info(s"Configuring feedback form schedulers for today's sessions")
      val feedbackFormsSchedulersFuture = scheduleFeedbackForms(sessionsFuture)

      feedbackFormsSchedulersFuture foreach { feedbackSchedulers =>
        feedbackFormsSchedulers = feedbackFormsSchedulers ++ feedbackSchedulers.flatten

        originalSender ! feedbackFormsSchedulers.size
      }
  }

  def reconfigurationHandler: PartialFunction[Any, Unit] = {
    case ScheduleFeedbackForms(oririnalSender) =>
      Logger.info(s"Starting schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val sessionsFuture =
        sessionsRepository.sessionsScheduledToday map { sessions =>
          sessions collect { case session
            if new Date(session.date.value).after(new Date(dateTimeUtility.nowMillis)) => session
          }
        }
      val feedbackFormsSchedulersFuture = scheduleFeedbackForms(sessionsFuture)

      feedbackFormsSchedulersFuture foreach { feedbackSchedulers =>
        feedbackFormsSchedulers = feedbackFormsSchedulers ++ feedbackSchedulers.flatten

        oririnalSender ! feedbackFormsSchedulers.size
      }
    case RefreshFeedbackFormSchedulers         =>
      Logger.info(s"Refreshing schedulers for Knolx sessions scheduled on ${dateTimeUtility.localDateIST}")
      val cancelled = feedbackFormsSchedulers.forall(_.cancel)

      if (feedbackFormsSchedulers.isEmpty || (feedbackFormsSchedulers.nonEmpty && cancelled)) {
        val sessionsFuture = sessionsRepository.sessionsScheduledToday
        val feedbackFormsSchedulersFuture = scheduleFeedbackForms(sessionsFuture)
        feedbackFormsSchedulersFuture foreach { feedbackSchedulers => feedbackFormsSchedulers = feedbackSchedulers.flatten }

        sender ! Restarted
      } else {
        sender ! NotRestarted
      }
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

      Logger.info(s"Email for session ${session.session} sent result $emailSent")
  }

  private def scheduleFeedbackForms(sessionsDateFuture: Future[List[SessionInfo]]): Future[List[Option[Cancellable]]] = {
    sessionsDateFuture flatMap { sessions =>
      Logger.info(s"Scheduling sessions today booked at ${sessions.map(session => new Date(session.date.value))}")

      Future.sequence {
        sessions map { session =>
          val feedbackFormFuture = feedbackFormsRepository.getByFeedbackFormId(session.feedbackFormId)
          val delay = (session.date.value - dateTimeUtility.nowMillis).milliseconds

          feedbackFormFuture map { maybeFeedbackForm =>
            maybeFeedbackForm.fold[Option[Cancellable]] {
              Logger.error(s"Something went wrong while getting feedback ${session.feedbackFormId}")
              None
            } { feedbackForm =>
              Some(scheduler.scheduleOnce(delay, self, SendFeedbackForm(session, feedbackForm)))
            }
          }
        }
      }
    }
  }

}

package schedulers

import javax.inject.Inject

import akka.actor.{Scheduler, Actor}
import FeedbackFormsScheduler._
import models.SessionsRepository
import play.api.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

object FeedbackFormsScheduler {

  case object SendFeedbackForm

  case class ScheduleFeedbackForms private[schedulers](initialDelay: FiniteDuration, interval: FiniteDuration)

}

class FeedbackFormsScheduler @Inject()(sessionsRepository: SessionsRepository) extends Actor {

  override def preStart(): Unit = {
    import scala.concurrent.duration._
    import scala.concurrent.Await

    val sessions = Await.result(sessionsRepository.sessionsScheduledToday, 10.seconds)
    Logger.info("Sessions " + sessions)

    self ! ScheduleFeedbackForms(15.days, 15.days)
  }

  def scheduler: Scheduler = context.system.scheduler

  def receive: Receive = {
    case ScheduleFeedbackForms(initialDelay, interval) =>
      scheduler.schedule(
        initialDelay = initialDelay,
        interval = interval,
        receiver = self,
        message = SendFeedbackForm
      )(context.dispatcher)

    case SendFeedbackForm                              => ???
  }

}

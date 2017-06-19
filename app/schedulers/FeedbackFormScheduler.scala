package schedulers

import javax.inject.Inject

import akka.actor.{Scheduler, Actor}
import FeedbackFormScheduler._
import models.SessionsRepository

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

object FeedbackFormScheduler {

  case object SendFeedbackForm

  case class ScheduleFeedbackForms private[schedulers](initialDelay: FiniteDuration, interval: FiniteDuration)

}

class FeedbackFormScheduler @Inject()(sessionsRepository: SessionsRepository) extends Actor {

  override def preStart(): Unit = {
    import scala.concurrent.duration._
    import scala.concurrent.Await

    val asd = Await.result(sessionsRepository.sessionsScheduledToday, 10.seconds)
    println(">>>>>>> asd " + asd)

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

/*
package schedulers

import akka.actor.{ActorSystem, Scheduler, Scope}
import org.scalatest.mock.MockitoSugar.mock
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions

class FeedbackFormScheduler extends Specification {
  implicit val system = ActorSystem("test")

  trait TestScope extends Scope {
    val mockedScheduler = mock[Scheduler]

    val feedbackFormScheduler = TestActorRef(new FeedbackFormScheduler())
  }

  "Feedback form scheduler" should {

    /*"start to schedule feedback form" in new TestScope {
      val initialDelay = 1.minute
      val interval = 1.minute

      emailScheduler ! ScheduleEmailReminder(initialDelay, interval)

      verify(emailScheduler.underlyingActor.scheduler)
        .schedule(initialDelay, interval, emailScheduler, SendEmails)(emailScheduler.underlyingActor.context.dispatcher)
    }*/

  }

}
*/

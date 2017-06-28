package controllers

import java.util.concurrent.TimeoutException

import akka.actor._
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Module}
import helpers.BeforeAllAfterAll
import play.api.Application
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import schedulers.FeedbackFormsScheduler.{ScheduledFeedbackForms, GetScheduledFeedbackForms, RefreshFeedbackFormSchedulers, Restarted}

import scala.concurrent.Await
import scala.concurrent.duration._

trait TestEnvironment extends BeforeAllAfterAll {
  val actorSystem: ActorSystem = ActorSystem("TestEnvironment")

  override def afterAll(): Unit = {
    shutdownActorSystem(actorSystem)
  }

  def fakeApp: Application = {
    val feedbackFormsScheduler = actorSystem.actorOf(Props(new DummyFeedbackFormsScheduler))

    val testModule = Option(new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[ActorRef])
          .annotatedWith(Names.named("FeedbackFormsScheduler"))
          .toInstance(feedbackFormsScheduler)
      }
    })

    new GuiceApplicationBuilder()
      .overrides(testModule.map(GuiceableModule.guiceable).toSeq: _*)
      .disable[Module]
      .build
  }

  protected def shutdownActorSystem(actorSystem: ActorSystem,
                                    duration: Duration = 10.seconds,
                                    verifySystemShutdown: Boolean = false): Unit = {
    actorSystem.terminate()

    try Await.ready(actorSystem.whenTerminated, duration) catch {
      case _: TimeoutException ⇒
        val msg = "Failed to stop [%s] within [%s]".format(actorSystem.name, duration)

        if (verifySystemShutdown) {
          throw new RuntimeException(msg)
        } else {
          actorSystem.log.warning(msg)
        }
    }
  }

  private class DummyFeedbackFormsScheduler extends Actor {
    def receive: Receive = {
      case RefreshFeedbackFormSchedulers => sender ! Restarted
      case GetScheduledFeedbackForms     => sender ! ScheduledFeedbackForms(List.empty)
    }
  }

}

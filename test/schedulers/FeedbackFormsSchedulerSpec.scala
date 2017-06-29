package schedulers

import akka.actor.{ActorSystem, Cancellable, Scheduler}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import models._
import org.mockito.Mockito.verify
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.mailer.{Email, MailerClient}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import schedulers.FeedbackFormsScheduler._
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class FeedbackFormsSchedulerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with WordSpecLike with DefaultAwaitTimeout with FutureAwaits with MustMatchers
  with Mockito with ImplicitSender with BeforeAndAfterAll {
  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {
    val mockedScheduler = mock[Scheduler]
    val sessionsRepository = mock[SessionsRepository]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val mailerClient = mock[MailerClient]
    val dateTimeUtility = mock[DateTimeUtility]

    val nowMillis = System.currentTimeMillis

    val knolxSessionDateTime = nowMillis + 24 * 60 * 60 * 1000

    val sessionId = BSONObjectID.generate
    val feedbackFormId = BSONObjectID.generate

    val sessionsScheduledToday =
      List(SessionInfo(
        userId = "userId",
        email = "test@example.com",
        date = BSONDateTime(knolxSessionDateTime),
        session = "session 1",
        feedbackFormId = "feedbackFormId",
        topic = "Play Framework",
        meetup = true,
        rating = "",
        cancelled = false,
        active = true,
        _id = sessionId))
    val maybeFeedbackForm =
      Option(FeedbackForm(
        name = "Feedback Form Template 1",
        questions = List(Question(question = "How good is the Knolx portal ?", options = List("1", "2", "3", "4", "5"))),
        active = true,
        _id = feedbackFormId))

    val feedbackFormScheduler =
      TestActorRef(
        new FeedbackFormsScheduler(sessionsRepository, feedbackFormsRepository, mailerClient, dateTimeUtility) {
          override def preStart(): Unit = {}

          override def scheduler: Scheduler = mockedScheduler
        })
  }

  "Feedback form scheduler" should {

    "start feedback forms scheduler" in new TestScope {
      val initialDelay = 1.minute
      val interval = 1.minute

      feedbackFormScheduler ! StartFeedbackFormsScheduler(initialDelay, interval)

      verify(feedbackFormScheduler.underlyingActor.scheduler)
        .schedule(
          initialDelay,
          interval,
          feedbackFormScheduler, ScheduleFeedbackForms)(feedbackFormScheduler.underlyingActor.context.dispatcher)
    }

    "schedule feedback forms" in new TestScope {
      sessionsRepository.sessionsScheduledToday returns Future.successful(sessionsScheduledToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      feedbackFormScheduler ! ScheduleFeedbackForms(self)

      expectMsg(1)
    }

    "start feedback forms schedulers for Knolx sessions scheduled today" in new TestScope {
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      val schedulersSize = feedbackFormScheduler ! ScheduleFeedbackFormsForToday(self, Future.successful(sessionsScheduledToday))

      expectMsg(1)
    }

    "refresh feedback forms schedulers" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = false
      }

      feedbackFormScheduler.underlyingActor.scheduledFeedbackForms = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsScheduledToday returns Future.successful(sessionsScheduledToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      val result = await((feedbackFormScheduler ? RefreshFeedbackFormSchedulers) (5.seconds).mapTo[FeedbackFormSchedulerResponses])

      result mustEqual Restarted
    }

    "get all scheduled feedback forms" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = true
      }

      feedbackFormScheduler.underlyingActor.scheduledFeedbackForms = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsScheduledToday returns Future.successful(sessionsScheduledToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      val result = await((feedbackFormScheduler ? GetScheduledFeedbackForms) (5.seconds).mapTo[ScheduledFeedbackForms])

      result mustEqual ScheduledFeedbackForms(List(sessionId.stringify))
    }

    "not refresh feedback forms schedulers because of empty feedback forms" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = false

        def isCancelled: Boolean = true
      }

      feedbackFormScheduler.underlyingActor.scheduledFeedbackForms = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsScheduledToday returns Future.successful(sessionsScheduledToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      val result = await((feedbackFormScheduler ? RefreshFeedbackFormSchedulers) (5.seconds).mapTo[FeedbackFormSchedulerResponses])

      result mustEqual NotRestarted
    }

    "send feedback form" in new TestScope {
      val feedbackFormEmail =
        Email(subject = s"${sessionsScheduledToday.head.topic} Feedback Form",
          from = "sidharth@knoldus.com",
          to = List("sidharth@knoldus.com"),
          bodyHtml = None,
          bodyText = Some("Hello World"), replyTo = None)

      mailerClient.send(feedbackFormEmail) returns "sent"

      feedbackFormScheduler ! SendFeedbackForm(sessionsScheduledToday.head, maybeFeedbackForm.get)

      verify(mailerClient).send(feedbackFormEmail)
    }

    "remove feedback form scheduler from the list of scheduled forms" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = true
      }

      feedbackFormScheduler.underlyingActor.scheduledFeedbackForms = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsScheduledToday returns Future.successful(sessionsScheduledToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      feedbackFormScheduler ! RemoveFeedbackFormScheduler(sessionId.stringify)

      expectMsg(0)
    }

  }

}

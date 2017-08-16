package actors

import java.time.{LocalDateTime, ZoneId}
import java.util.TimeZone

import actors.SessionsScheduler._
import akka.actor.{ActorRef, ActorSystem, Cancellable, Scheduler}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.inject.name.Names
import controllers.TestEnvironment
import models.SessionJsonFormats.{ExpiringNext, SchedulingNext}
import models._
import org.mockito.Mockito.verify
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class SessionsSchedulerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {

    lazy val app: Application = fakeApp()
    val mockedScheduler = mock[Scheduler]
    val sessionsRepository = mock[SessionsRepository]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val mailerClient = mock[MailerClient]
    val dateTimeUtility = mock[DateTimeUtility]

    val nowMillis = System.currentTimeMillis

    val knolxSessionDateTime = nowMillis + 24 * 60 * 60 * 1000

    val sessionId = BSONObjectID.generate
    val feedbackFormId = BSONObjectID.generate

    val emailManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

    val ISTZoneId = ZoneId.of("Asia/Kolkata")
    val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    val sessionsForToday =
      List(SessionInfo(
        userId = "userId",
        email = "test@example.com",
        date = BSONDateTime(knolxSessionDateTime),
        session = "session 1",
        feedbackFormId = "feedbackFormId",
        topic = "Play Framework",
        feedbackExpirationDays = 1,
        meetup = true,
        rating = "",
        cancelled = false,
        active = true,
        BSONDateTime(knolxSessionDateTime),
        _id = sessionId))
    val maybeFeedbackForm =
      Option(FeedbackForm(
        name = "Feedback Form Template 1",
        questions = List(Question(question = "How good is the Knolx portal ?", options = List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)),
        active = true,
        _id = feedbackFormId))

    val sessionsScheduler =
      TestActorRef(
        new SessionsScheduler(sessionsRepository, usersRepository, feedbackFormsRepository, config, emailManager, dateTimeUtility) {
          override def preStart(): Unit = {}

          override def scheduler: Scheduler = mockedScheduler
        })
  }

  "Sessions scheduler" should {

    "start sessions scheduler" in new TestScope {
      val initialDelay = 1.minute
      val interval = 1.minute

      sessionsScheduler ! StartSessionsScheduler(initialDelay, interval)

      verify(sessionsScheduler.underlyingActor.scheduler)
        .schedule(
          initialDelay,
          interval,
          sessionsScheduler, ScheduleSessions)(sessionsScheduler.underlyingActor.context.dispatcher)
    }

    "start feedback reminder mail Scheduler" in new TestScope {
      val initialDelay = 1.minute
      val interval = 1.minute

      sessionsScheduler ! StartFeedbackReminderMailScheduler(initialDelay, interval)

      verify(sessionsScheduler.underlyingActor.scheduler)
        .schedule(
          initialDelay,
          interval,
          sessionsScheduler, ScheduleSessions)(sessionsScheduler.underlyingActor.context.dispatcher)
    }

    "schedule sessions" in new TestScope {
      sessionsRepository.sessionsForToday(SchedulingNext) returns Future.successful(sessionsForToday)
      sessionsScheduler ! ScheduleSessions(self)

      expectMsg(1)
    }

    "schedule reminders" in new TestScope {

      sessionsRepository.sessionsForToday(ExpiringNext) returns Future.successful(sessionsForToday)
      dateTimeUtility.toLocalDate(sessionsForToday.head.date.value) returns LocalDateTime.now(ISTZoneId).toLocalDate
      sessionsScheduler ! ScheduleRemainder(self)

      expectMsg(1)
    }

    "start sessions schedulers for Knolx sessions scheduled today" in new TestScope {

      val schedulersSize = sessionsScheduler ! ScheduleSessionsForToday(self, Future.successful(sessionsForToday))

      expectMsg(1)
    }

    "start sessions reminders schedulers for Knolx sessions expiring today" in new TestScope {
      dateTimeUtility.toLocalDate(sessionsForToday.head.date.value) returns LocalDateTime.now(ISTZoneId).toLocalDate
      val schedulersSize = sessionsScheduler ! SendReminderMailForToday(self, Future.successful(sessionsForToday))

      expectMsg(1)
    }

    "refresh sessions schedulers" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = false
      }

      sessionsScheduler.underlyingActor.scheduledEmails = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsForToday(SchedulingNext) returns Future.successful(sessionsForToday)
      sessionsRepository.sessionsForToday(ExpiringNext) returns Future.successful(sessionsForToday)

      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      val result = await((sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse])

      result mustEqual ScheduledSessionsRefreshed
    }

    "get all scheduled sessions" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = true
      }

      sessionsScheduler.underlyingActor.scheduledEmails = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsForToday(SchedulingNext) returns Future.successful(sessionsForToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      val result = await((sessionsScheduler ? GetScheduledSessions) (5.seconds).mapTo[ScheduledSessions])

      result mustEqual ScheduledSessions(List(sessionId.stringify))
    }

    "not refresh sessions schedulers because of empty feedback forms" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = false

        def isCancelled: Boolean = true
      }

      sessionsScheduler.underlyingActor.scheduledEmails = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsForToday(SchedulingNext) returns Future.successful(sessionsForToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      val result = await((sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse])

      result mustEqual ScheduledSessionsNotRefreshed
    }

    "send feedback form" in new TestScope {
      val feedbackFormEmail =
        Email(subject = s"${sessionsForToday.head.topic} Feedback Form",
          from = "test@example.com",
          to = List("test@example.com"),
          bodyHtml = None,
          bodyText = Some("Hello World"), replyTo = None)

      usersRepository.getAllActiveEmails returns Future.successful(List("test@example.com"))

      sessionsScheduler ! SendEmail(sessionsForToday, reminder = false)

      sessionsScheduler.underlyingActor.scheduledEmails.keys must not(contain(sessionId.stringify))
    }

    "send reminder form" in new TestScope {
      val feedbackFormEmail =
        Email(subject = s"${sessionsForToday.head.topic} Feedback Form",
          from = "test@example.com",
          to = List("test@example.com"),
          bodyHtml = None,
          bodyText = Some(" knolx reminder"), replyTo = None)

      usersRepository.getAllActiveEmails returns Future.successful(List("test@example.com"))

      sessionsScheduler ! SendEmail(sessionsForToday, reminder = true)

      sessionsScheduler.underlyingActor.scheduledEmails.keys must not(contain(LocalDateTime.now(ISTZoneId).toLocalDate.toString))
    }

    "cancel a scheduled session" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = true
      }

      sessionsScheduler.underlyingActor.scheduledEmails = Map(sessionId.stringify -> cancellable)

      sessionsRepository.sessionsForToday(SchedulingNext) returns Future.successful(sessionsForToday)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      sessionsScheduler ! CancelScheduledSession(sessionId.stringify)

      expectMsg(true)
    }

    "schedule a session" in new TestScope {
      sessionsRepository.getById(sessionId.stringify) returns Future.successful(sessionsForToday.headOption)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(maybeFeedbackForm)

      sessionsScheduler ! ScheduleSession(sessionId.stringify)

      expectMsg(true)
      sessionsScheduler.underlyingActor.scheduledEmails.keys must contain(sessionId.stringify)
    }
  }

}

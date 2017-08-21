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
    val mockedScheduler: Scheduler = mock[Scheduler]
    val sessionsRepository: SessionsRepository = mock[SessionsRepository]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]
    val mailerClient: MailerClient = mock[MailerClient]
    val dateTimeUtility: DateTimeUtility = mock[DateTimeUtility]

    val nowMillis: Long = System.currentTimeMillis

    val knolxSessionDateTime: Long = nowMillis + 24 * 60 * 60 * 1000

    val sessionId: BSONObjectID = BSONObjectID.generate
    val feedbackFormId: BSONObjectID = BSONObjectID.generate

    val emailManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

    val ISTZoneId: ZoneId = ZoneId.of("Asia/Kolkata")
    val ISTTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

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
      val initialDelay: FiniteDuration = 1.minute
      val interval: FiniteDuration = 1.minute

      sessionsScheduler ! InitiateFeedbackEmailsStartingTomorrow(initialDelay, interval)

      verify(sessionsScheduler.underlyingActor.scheduler)
        .schedule(
          initialDelay,
          interval,
          sessionsScheduler,
          ScheduleFeedbackEmailsStartingTomorrow)(sessionsScheduler.underlyingActor.context.dispatcher)
    }

    "start feedback reminder mail Scheduler" in new TestScope {
      val initialDelay: FiniteDuration = 1.minute
      val interval: FiniteDuration = 1.minute

      sessionsScheduler ! InitialFeedbackRemindersStartingTomorrow(initialDelay, interval)

      verify(sessionsScheduler.underlyingActor.scheduler)
        .schedule(
          initialDelay,
          interval,
          sessionsScheduler,
          ScheduleFeedbackRemindersStartingTomorrow)(sessionsScheduler.underlyingActor.context.dispatcher)
    }

    "schedule sessions" in new TestScope {
      sessionsRepository.sessionsForToday(SchedulingNext) returns Future.successful(sessionsForToday)
      sessionsScheduler ! ScheduleFeedbackEmailsStartingTomorrow

      sessionsScheduler.underlyingActor.scheduledEmails.size must_=== 1
    }

    "schedule reminders" in new TestScope {

      sessionsRepository.sessionsForToday(ExpiringNext) returns Future.successful(sessionsForToday)
      dateTimeUtility.toLocalDate(sessionsForToday.head.date.value) returns LocalDateTime.now(ISTZoneId).toLocalDate
      sessionsScheduler ! ScheduleFeedbackRemindersStartingTomorrow

      sessionsScheduler.underlyingActor.scheduledEmails.size must_=== 1
    }

    "start sessions schedulers for Knolx sessions scheduled today" in new TestScope {

      sessionsScheduler ! ScheduleFeedbackEmailsStartingToday(self, Future.successful(sessionsForToday))

      expectMsg(1)
    }

    "start sessions reminders schedulers for Knolx sessions expiring today" in new TestScope {
      dateTimeUtility.toLocalDate(sessionsForToday.head.date.value) returns LocalDateTime.now(ISTZoneId).toLocalDate
      sessionsScheduler ! ScheduleFeedbackRemindersStartingToday(self, Future.successful(sessionsForToday))

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

      val result: SessionsSchedulerResponse = await((sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse])

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

      val result: ScheduledSessions = await((sessionsScheduler ? GetScheduledSessions) (5.seconds).mapTo[ScheduledSessions])

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

      val result: SessionsSchedulerResponse = await((sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse])

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

      sessionsScheduler.underlyingActor.scheduledEmails.keys must contain(sessionId.stringify)
    }

  }

}

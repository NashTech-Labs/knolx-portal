package actors

import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.TimeZone

import actors.SessionsScheduler.{ScheduleSessionNotificationStartingTomorrow, _}
import akka.actor.{ActorRef, ActorSystem, Cancellable, Scheduler}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.inject.name.Names
import helpers.TestEnvironment
import models.SessionJsonFormats.SchedulingNext
import models._
import org.mockito.Mockito.verify
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class SessionsSchedulerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    system.terminate()
  }

  trait TestScope extends Scope {

    lazy val app: Application = fakeApp()
    val mockedScheduler: Scheduler = mock[Scheduler]
    val sessionsRepository: SessionsRepository = mock[SessionsRepository]
    val feedbackFormsRepository: FeedbackFormsRepository = mock[FeedbackFormsRepository]
    val feedbackFormsResponseRepository: FeedbackFormsResponseRepository = mock[FeedbackFormsResponseRepository]
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
        category = "category",
        subCategory = "subCategory",
        feedbackFormId = "feedbackFormId",
        topic = "Akka",
        feedbackExpirationDays = 1,
        meetup = true,
        rating = "",
        score = 0.00,
        cancelled = false,
        active = true,
        BSONDateTime(knolxSessionDateTime),
        youtubeURL = Some("youtubeURL"),
        slideShareURL = Some("slideShareURL"),
        _id = sessionId),
        SessionInfo(
          userId = "userId",
          email = "test1@example.com",
          date = BSONDateTime(knolxSessionDateTime),
          session = "session 1",
          category = "category",
          subCategory = "subCategory",
          feedbackFormId = "feedbackFormId",
          topic = "Play Framework",
          feedbackExpirationDays = 1,
          meetup = true,
          rating = "",
          score = 0.00,
          cancelled = false,
          active = true,
          BSONDateTime(knolxSessionDateTime),
          youtubeURL = Some("youtubeURL"),
          slideShareURL = Some("slideShareURL"),
          _id = sessionId))
    val maybeFeedbackForm =
      Option(FeedbackForm(
        name = "Feedback Form Template 1",
        questions = List(Question(question = "How good is the Knolx portal ?", options = List("1", "2", "3", "4", "5"), "MCQ", mandatory = true)),
        active = true,
        _id = feedbackFormId))
    val sessionsScheduler =
      TestActorRef(
        new SessionsScheduler(sessionsRepository, usersRepository, feedbackFormsRepository, feedbackFormsResponseRepository, config, emailManager, dateTimeUtility) {
          override def preStart(): Unit = {}

          override def scheduler: Scheduler = mockedScheduler
        })
    private val ZoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))
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

    "start session notification mail Scheduler" in new TestScope {
      val initialDelay: FiniteDuration = 1.minute
      val interval: FiniteDuration = 1.minute

      sessionsScheduler ! InitialSessionNotificationsStartingTomorrow(initialDelay, interval)

      verify(sessionsScheduler.underlyingActor.scheduler)
        .schedule(
          initialDelay,
          interval,
          sessionsScheduler,
          ScheduleSessionNotificationStartingTomorrow)(sessionsScheduler.underlyingActor.context.dispatcher)
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

    "send feedback form" in new TestScope {
      val feedbackFormEmail =
        Email(subject = s"${sessionsForToday.head.topic} Feedback Form",
          from = "test@example.com",
          to = List("test@example.com"),
          bodyHtml = None,
          bodyText = Some("Hello World"), replyTo = None)

      usersRepository.getAllActiveEmails returns Future.successful(List("test@example.com", "test1@example.com"))

      sessionsScheduler ! SendEmail(sessionsForToday, Feedback)

      sessionsScheduler.underlyingActor.scheduledEmails.keys must not(contain(sessionId.stringify))
    }

    "send reminder form" in new TestScope {
      val feedbackFormEmail =
        Email(subject = s"${sessionsForToday.head.topic} Reminder Feedback Form",
          from = "test@example.com",
          to = List("test1@example.com"),
          bodyHtml = None,
          bodyText = Some(" knolx reminder"), replyTo = None)
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getAllActiveEmails returns Future.successful(List("test@example.com", "test1@example.com","test2@example.com"))

      dateTimeUtility.toLocalDate(knolxSessionDateTime) returns Instant.ofEpochMilli(knolxSessionDateTime).atZone(ISTZoneId).toLocalDate

      sessionsRepository.upsertRecord(sessionsForToday.head, Reminder) returns updateWriteResult

      sessionsRepository.upsertRecord(sessionsForToday(1), Reminder) returns updateWriteResult

      sessionsScheduler ! SendEmail(sessionsForToday, Reminder)

      sessionsScheduler.underlyingActor.scheduledEmails.keys must not(contain(LocalDateTime.now(ISTZoneId).toLocalDate.toString))
    }

    "send notification" in new TestScope {
      val feedbackFormEmail =
        Email(subject = s"${sessionsForToday.head.topic} Feedback Form",
          from = "test@example.com",
          to = List("test@example.com", "test2@example.com"),
          bodyHtml = None,
          bodyText = Some(" knolx reminder"), replyTo = None)

      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getAllActiveEmails returns Future.successful(List("test@example.com", "test1@example.com"))

      dateTimeUtility.toLocalDate(knolxSessionDateTime) returns Instant.ofEpochMilli(knolxSessionDateTime).atZone(ISTZoneId).toLocalDate

      sessionsRepository.upsertRecord(sessionsForToday.head, Notification) returns updateWriteResult

      sessionsRepository.upsertRecord(sessionsForToday(1), Notification) returns updateWriteResult

      sessionsScheduler ! SendEmail(sessionsForToday, Notification)

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

  }

}

package actors

import java.time.ZoneId
import java.util.TimeZone

import actors.UsersBanScheduler.{SendEmail, _}
import akka.actor.{ActorRef, ActorSystem, Cancellable, Scheduler}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.inject.name.Names
import controllers.TestEnvironment
import models.SessionJsonFormats.ExpiringNext
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

class UsersBanSchedulerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {

    lazy val app: Application = fakeApp()
    val mockedScheduler: Scheduler = mock[Scheduler]
    val sessionsRepository: SessionsRepository = mock[SessionsRepository]
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
        feedbackFormId = "feedbackFormId",
        topic = "Play Framework",
        feedbackExpirationDays = 1,
        meetup = true,
        rating = "",
        cancelled = false,
        active = true,
        BSONDateTime(knolxSessionDateTime),
        _id = sessionId))

    val usersBanScheduler =
      TestActorRef(
        new UsersBanScheduler(sessionsRepository, usersRepository, feedbackFormsResponseRepository, config, emailManager, dateTimeUtility) {
          override def preStart(): Unit = {}

          override def scheduler: Scheduler = mockedScheduler
        })

  }

  "Sessions scheduler" should {

    "start sessions scheduler" in new TestScope {
      val initialDelay: FiniteDuration = 1.minute
      val interval: FiniteDuration = 1.minute

      usersBanScheduler ! InitiateBanEmails(initialDelay, interval)

      verify(usersBanScheduler.underlyingActor.scheduler)
        .schedule(
          initialDelay,
          interval,
          usersBanScheduler, ScheduleBanEmails)(usersBanScheduler.underlyingActor.context.dispatcher)
    }

    "schedule Ban emails with banning user" in new TestScope {
      feedbackFormsResponseRepository.getAllResponseEmailsPerSession(sessionId.stringify) returns Future.successful(List("test@example.com"))
      sessionsRepository.sessionsForToday(ExpiringNext) returns Future.successful(sessionsForToday)
      usersRepository.getAllActiveEmails returns Future.successful(List("testother@example.com"))
      dateTimeUtility.endOfDayMillis returns knolxSessionDateTime
      dateTimeUtility.nowMillis returns knolxSessionDateTime
      usersBanScheduler ! ScheduleBanEmails

      usersBanScheduler.underlyingActor.scheduledBanEmails.size must_=== 1
    }

    "refresh ban users schedulers" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = false
      }

      usersBanScheduler.underlyingActor.scheduledBanEmails = Map(sessionId.stringify -> cancellable)
      feedbackFormsResponseRepository.getAllResponseEmailsPerSession(sessionId.stringify) returns Future.successful(List("test@example.com"))
      sessionsRepository.sessionsForToday(ExpiringNext) returns Future.successful(sessionsForToday)
      usersRepository.getAllActiveEmails returns Future.successful(List("testother@example.com"))
      dateTimeUtility.endOfDayMillis returns knolxSessionDateTime
      dateTimeUtility.nowMillis returns knolxSessionDateTime

      val result: SessionBanSchedulerResponse = await((usersBanScheduler ? RefreshSessionsBanSchedulers) (5.seconds).mapTo[SessionBanSchedulerResponse])

      result mustEqual ScheduledBanSessionsRefreshed
    }

    "not refresh users ban schedulers because of empty scheduled bans" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = false

        def isCancelled: Boolean = true
      }
      usersBanScheduler.underlyingActor.scheduledBanEmails = Map(sessionId.stringify -> cancellable)

      val result: SessionBanSchedulerResponse = await((usersBanScheduler ? RefreshSessionsBanSchedulers) (5.seconds).mapTo[SessionBanSchedulerResponse])

      result mustEqual ScheduledBanSessionsNotRefreshed
    }

    "send ban email" in new TestScope {
      val feedbackFormEmail =
        Email(subject = "Knolx ban Form",
          from = "test@example.com",
          to = List("test@example.com"),
          bodyHtml = None,
          bodyText = Some("Hello World"), replyTo = None)

      usersRepository.ban("test@example.com") returns Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      usersBanScheduler ! SendEmail(EmailContent("test@example.com", List(EmailBodyInfo("topic", "presenter", "date"))))

      usersBanScheduler.underlyingActor.scheduledBanEmails.keys must not(contain("test@example.com"))
    }

  }

}

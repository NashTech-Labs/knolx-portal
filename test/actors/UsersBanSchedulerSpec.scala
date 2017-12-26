package actors

import java.util.Date

import actors.UsersBanScheduler.{SendEmail, _}
import akka.actor.{ActorRef, ActorSystem, Cancellable, Scheduler}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.inject.name.Names
import helpers.TestEnvironment
import models.SessionJsonFormats.ExpiringNext
import models._
import org.mockito.Mockito.verify
import org.mockito.stubbing.OngoingStubbing
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.mailer.Email
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
    system.terminate()
  }

  trait TestScope extends Scope {

    lazy val app: Application = fakeApp()
    val mockedScheduler: Scheduler = mock[Scheduler]
    val sessionsRepository: SessionsRepository = mock[SessionsRepository]
    val feedbackFormsResponseRepository: FeedbackFormsResponseRepository = mock[FeedbackFormsResponseRepository]
    val dateTimeUtility: DateTimeUtility = mock[DateTimeUtility]

    val nowMillis: Long = System.currentTimeMillis
    val knolxSessionDateTime: Long = nowMillis + 24 * 60 * 60 * 1000

    val sessionId: BSONObjectID = BSONObjectID.generate

    val activeEmails = List("test@example.com", "test1@example.com")
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
        rating = "25",
        score = 0.00,
        cancelled = false,
        active = true,
        BSONDateTime(knolxSessionDateTime),
        youtubeURL = Some("youtubeURL"),
        slideShareURL = Some("slideShareURL"),
        _id = sessionId))

    val emailManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

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

    "session expiring today" in new TestScope {
      sessionsRepository.sessionsForToday(ExpiringNext) returns Future.successful(sessionsForToday)

      usersBanScheduler.underlyingActor.sessionsExpiringToday.map(session => session mustEqual sessionsForToday)
    }

    "scheduled ban emails" in new TestScope {
      override val usersBanScheduler = TestActorRef(
        new UsersBanScheduler(sessionsRepository, usersRepository, feedbackFormsResponseRepository, config, emailManager, dateTimeUtility) {
          override def preStart(): Unit = {}

          override def sessionsExpiringToday: Future[List[SessionInfo]] = Future.successful(sessionsForToday)

          override def getBanInfo(sessions: List[SessionInfo], emails: List[String]): Future[List[EmailContent]] = {
            Future.successful(
              List(EmailContent("test1@example.com",
                List(EmailBodyInfo("Akka", sessionsForToday.head.email,
                  new Date(sessionsForToday.head.date.value).toString)))))
          }

          override def scheduler: Scheduler = mockedScheduler

          override def scheduleBanEmails(eventualExpiringSessions: Future[List[SessionInfo]]): Future[Map[String, Cancellable]] = {
            val cancellable = new Cancellable {
              def cancel(): Boolean = true

              def isCancelled: Boolean = true
            }

            Future.successful(Map("test1@example.com" -> cancellable))
          }
        })

      usersBanScheduler ! ScheduleBanEmails

      expectNoMsg()
    }

    "scheduleBanEmails function" in new TestScope {
      val cancellable = new Cancellable {
        def cancel(): Boolean = true

        def isCancelled: Boolean = true
      }
      override val usersBanScheduler = TestActorRef(
        new UsersBanScheduler(sessionsRepository, usersRepository, feedbackFormsResponseRepository, config, emailManager, dateTimeUtility) {
          override def preStart(): Unit = {}

          override def getBanInfo(sessions: List[SessionInfo], emails: List[String]): Future[List[EmailContent]] = {
            Future.successful(
              List(EmailContent("test1@example.com",
                List(EmailBodyInfo("Akka", sessionsForToday.head.email,
                  new Date(sessionsForToday.head.date.value).toString)))))
          }

          override def scheduler: Scheduler = mockedScheduler
        })

      usersRepository.getAllActiveEmails returns Future.successful(activeEmails)

      usersBanScheduler.underlyingActor.scheduleBanEmails(Future.successful(sessionsForToday)).map( session =>
        session.mustEqual(Map("test1@example.com" -> cancellable))
      )
    }

    "return get Ban info" in new TestScope {
      feedbackFormsResponseRepository.getAllResponseEmailsPerSession(sessionsForToday.head._id.stringify) returns Future.successful(Nil)

      usersBanScheduler.underlyingActor.getBanInfo(sessionsForToday, activeEmails).map( information =>
      information.mustEqual(
        List(EmailContent("test1@example.com",
          List(EmailBodyInfo("Akka", sessionsForToday.head.email,
            new Date(sessionsForToday.head.date.value).toString))))))
    }

    "return ScheduledBannedUsers" in new TestScope {
      override val usersBanScheduler = TestActorRef(
        new UsersBanScheduler(sessionsRepository, usersRepository, feedbackFormsResponseRepository, config, emailManager, dateTimeUtility) {
          override def preStart(): Unit = {}
          override def scheduler: Scheduler = mockedScheduler
        })

      usersBanScheduler ! GetScheduledBannedUsers

      usersBanScheduler.underlyingActor.scheduledBanEmails mustEqual Map.empty
    }

  }

}

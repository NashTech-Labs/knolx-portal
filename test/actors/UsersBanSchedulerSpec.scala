package actors

import actors.UsersBanScheduler.{SendEmail, _}
import akka.actor.{ActorRef, ActorSystem, Scheduler}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.inject.name.Names
import helpers.TestEnvironment
import models._
import org.mockito.Mockito.verify
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.mailer.Email
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONObjectID
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

  }

}

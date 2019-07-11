package helpers

import java.util.concurrent.TimeoutException

import actors._
import akka.actor._
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoCategory}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Module}
import com.typesafe.config.ConfigFactory
import controllers._
import models.UsersRepository
import org.apache.commons.mail.EmailException
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import play.api.http._
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.mailer.MailerClient
import play.api.libs.streams.Accumulator
import play.api.mvc.{BodyParser, _}
import play.api.test._
import play.api.{Application, Configuration}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object TestHelpers extends PlayRunners
  with HeaderNames
  with Status
  with MimeTypes
  with HttpProtocol
  with DefaultAwaitTimeout
  with ResultExtractors
  with Writeables
  with EssentialActionCaller
  with RouteInvokers
  with FutureAwaits
  with TestStubControllerComponentsFactory

trait TestStubControllerComponentsFactory extends StubPlayBodyParsersFactory with StubBodyParserFactory with StubMessagesFactory {

  def stubControllerComponents(usersRepository: UsersRepository, config: Configuration): KnolxControllerComponents = {
    val bodyParser = stubBodyParser(AnyContentAsEmpty)
    val executionContext = ExecutionContext.global

    DefaultKnolxControllerComponents(
      DefaultActionBuilder(bodyParser)(executionContext),
      UserActionBuilder(bodyParser, usersRepository, config)(executionContext),
      AdminActionBuilder(bodyParser, usersRepository, config)(executionContext),
      SuperUserActionBuilder(bodyParser, usersRepository, config)(executionContext),
      stubPlayBodyParsers(NoMaterializer),
      stubMessagesApi(),
      stubLangs(),
      new DefaultFileMimeTypes(FileMimeTypesConfiguration()),
      executionContext)
  }

  override def stubBodyParser[T](content: T = AnyContentAsEmpty): BodyParser[T] = {
    BodyParser(_ => Accumulator.done(Right(content)))
  }

}

// =====================================================================================================================
// =====================================================================================================================
// =====================================================================================================================
// =====================================================================================================================
// =====================================================================================================================

trait TestEnvironment extends SpecificationLike with BeforeAllAfterAll with Mockito {

  val usersRepository: UsersRepository = mock[UsersRepository]
  val config = Configuration(ConfigFactory.load("application.conf"))
  val knolxControllerComponent: KnolxControllerComponents = TestHelpers.stubControllerComponents

  private val actorSystem: ActorSystem = ActorSystem("TestEnvironment")

  override def afterAll(): Unit = {
    shutdownActorSystem(actorSystem)
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

  protected def fakeApp(system: ActorSystem = actorSystem): Application = {
    val sessionsScheduler = system.actorOf(Props(new DummySessionsScheduler))
    val usersBanScheduler = system.actorOf(Props(new DummyUsersBanScheduler))
    val youtubeProgressManager = system.actorOf(Props(new DummyYouTubeProgressManager))
    val youtubeManager = system.actorOf(Props(new DummyYouTubeManager))
    val mailerClient = mock[MailerClient]

    val testModule = Option(new AbstractModule with AkkaGuiceSupport {
      override def configure(): Unit = {
        bind(classOf[ActorRef])
          .annotatedWith(Names.named("SessionsScheduler"))
          .toInstance(sessionsScheduler)

        bind(classOf[ActorRef])
          .annotatedWith(Names.named("UsersBanScheduler"))
          .toInstance(usersBanScheduler)

        bindActorFactory[TestEmailActor, ConfiguredEmailActor.Factory]
        bindActor[EmailManager]("EmailManager")

        bindActorFactory[DummyYouTubeUploader, ConfiguredYouTubeUploader.Factory]
        bindActorFactory[DummyYouTubeDetailsActor, ConfiguredYouTubeDetailsActor.Factory]
        bind(classOf[ActorRef])
          .annotatedWith(Names.named("YouTubeManager"))
          .toInstance(youtubeManager)

        bind(classOf[ActorRef])
          .annotatedWith(Names.named("YouTubeProgressManager"))
          .toInstance(youtubeProgressManager)

        bind(classOf[KnolxControllerComponents])
          .toInstance(knolxControllerComponent)

        bind(classOf[MailerClient]).toInstance(mailerClient)
      }
    })

    new GuiceApplicationBuilder()
      .overrides(testModule.map(GuiceableModule.guiceable).toSeq: _*)
      .disable[Module]
      .build
  }

  trait TestStubControllerComponentsFactory extends StubPlayBodyParsersFactory with StubBodyParserFactory with StubMessagesFactory {

    def stubControllerComponents: KnolxControllerComponents = {
      val bodyParser = stubBodyParser(AnyContentAsEmpty)
      val executionContext = ExecutionContext.global

      DefaultKnolxControllerComponents(
        DefaultActionBuilder(bodyParser)(executionContext),
        UserActionBuilder(bodyParser, usersRepository, config)(executionContext),
        AdminActionBuilder(bodyParser, usersRepository, config)(executionContext),
        SuperUserActionBuilder(bodyParser, usersRepository, config)(executionContext),
        stubPlayBodyParsers(NoMaterializer),
        stubMessagesApi(),
        stubLangs(),
        new DefaultFileMimeTypes(FileMimeTypesConfiguration()),
        executionContext)
    }

    override def stubBodyParser[T](content: T = AnyContentAsEmpty): BodyParser[T] = {
      BodyParser(_ => Accumulator.done(Right(content)))
    }

  }

  object TestHelpers extends PlayRunners
    with HeaderNames
    with Status
    with MimeTypes
    with HttpProtocol
    with DefaultAwaitTimeout
    with ResultExtractors
    with Writeables
    with EssentialActionCaller
    with RouteInvokers
    with FutureAwaits
    with TestStubControllerComponentsFactory

}

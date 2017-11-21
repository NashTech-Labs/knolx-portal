package controllers

import java.util.concurrent.TimeoutException

import actors.SessionsScheduler._
import actors.UsersBanScheduler.GetScheduledBannedUsers
import actors._
import akka.actor._
import com.google.api.services.youtube.model.{Video, VideoCategory}
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Module}
import com.typesafe.config.ConfigFactory
import helpers.BeforeAllAfterAll
import models.UsersRepository
import org.apache.commons.mail.EmailException
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import play.api.http._
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.streams.Accumulator
import play.api.mvc.{BodyParser, _}
import play.api.test._
import play.api.{Application, Configuration, Logger}

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

abstract class TestEnvironment1(system: ActorSystem) extends SpecificationLike with BeforeAllAfterAll {

  override def afterAll(): Unit = {
    shutdownActorSystem(system)
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

  protected def fakeApp(testModule: Option[AbstractModule] = None): Application =
    new GuiceApplicationBuilder()
      .overrides(testModule.map(GuiceableModule.guiceable).toSeq: _*)
      .disable[Module]
      .build

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
    val youtubeUploadManager = system.actorOf(Props(new DummyYouTubeUploadManager))

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
        bindActorFactory[DummyYouTubeCategoryActor, ConfiguredYouTubeCategoryActor.Factory]
        bindActor[YouTubeUploaderManager]("YouTubeUploaderManager")

        bind(classOf[ActorRef])
          .annotatedWith(Names.named("YouTubeUploadManager"))
          .toInstance(youtubeUploadManager)

        bind(classOf[KnolxControllerComponents])
          .toInstance(knolxControllerComponent)
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

class DummySessionsScheduler extends Actor {

  def receive: Receive = {
    case GetScheduledSessions              => sender ! ScheduledSessions(List.empty)
    case CancelScheduledSession(sessionId) => sender ! true
    case ScheduleSession(sessionId)        => sender ! true
  }

}

class DummyUsersBanScheduler extends Actor {

  def receive: Receive = {
    case GetScheduledBannedUsers => sender ! List.empty
  }

}

class TestEmailActor extends Actor {

  def receive: Receive = {
    case EmailActor.SendEmail(_, _, subject, _) if subject == "crash" => throw new EmailException
    case request: EmailActor.SendEmail                                => sender ! request
  }

}

class DummyYouTubeUploadManager extends Actor {

  override def receive: Receive = {
    case YouTubeUploadManager.VideoId(sessionId)               =>
      Logger.info("Getting from sessionVideos")
      sender() ! Some(new Video)
    case YouTubeUploadManager.VideoUploader(sessionId: String) => sender() ! Some(50D)
  }

}

class DummyYouTubeUploader extends Actor {

  override def receive: Receive = {
    case YouTubeUploader.VideoDetails => sender() ! "Successfully updated the video details"
  }

}

class DummyYouTubeCategoryActor extends Actor {

  override def receive: Receive = {
    case Categories => sender() ! List[VideoCategory]()
  }

}
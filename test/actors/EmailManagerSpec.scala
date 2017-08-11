package actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import controllers.{TestEmailActor, TestEnvironment}
import org.apache.commons.mail.EmailException
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.BindingKey
import play.api.libs.concurrent.InjectedActorSupport
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

class EmailManagerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with WordSpecLike with DefaultAwaitTimeout with FutureAwaits with MustMatchers
  with Mockito with ImplicitSender with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  object TestEmailActorFactory extends actors.ConfiguredEmailActor.Factory {
    def apply(): Actor = {
      val actorRef = TestActorRef[TestEmailActor]
      actorRef.underlyingActor
    }
  }

  trait UnitTestScope extends Scope {
    sealed trait TestInjectedActorSupport extends InjectedActorSupport {
      override def injectedChild(create: => Actor, name: String, props: Props => Props = identity)(implicit context: ActorContext): ActorRef = {
        TestActorRef[TestEmailActor]
      }
    }

    val emailManager = TestActorRef(new EmailManager(TestEmailActorFactory) with TestInjectedActorSupport)
  }

  abstract class IntegrationTestScope extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp(_system)
    lazy val configuredEmailFactory = app.injector.instanceOf(BindingKey(classOf[ConfiguredEmailActor.Factory]))
    lazy val emailManager = TestActorRef(new EmailManager(configuredEmailFactory))

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "Email manager" should {

    // =================================================================================================================
    // Unit tests
    // =================================================================================================================
    "forward request to email actor" in new UnitTestScope {
      val request = EmailActor.SendEmail(List("test@example.com"), "test@example.com", "Hello World", "Hello World!")

      emailManager ! request

      expectMsg(request)
    }

    "send message to dead letters for unhandled message" in new UnitTestScope {
      emailManager ! "blah!"

      expectNoMsg
    }

    "test supervision strategy" in new UnitTestScope {
      val strategy = emailManager.underlyingActor.supervisorStrategy.decider

      val supervisorAction = strategy(new EmailException)

      supervisorAction mustEqual Restart
    }

    // =================================================================================================================
    // Integration tests
    // =================================================================================================================
    "send email" in new IntegrationTestScope {
      val request = EmailActor.SendEmail(List("test@example.com"), "test@example.com", "Hello World", "Hello World!")

      emailManager ! request

      expectMsg(request)
    }

    "restart email actor" in new IntegrationTestScope {
      val badRequest = EmailActor.SendEmail(List("test@example.com"), "test@example.com", "crash", "Hello World!")
      val request = EmailActor.SendEmail(List("test@example.com"), "test@example.com", "Hello World", "Hello World!")

      emailManager ! badRequest

      expectNoMsg

      emailManager ! request

      expectMsg(request)
    }
  }

}

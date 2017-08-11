package actors

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.apache.commons.mail.EmailException
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.concurrent.InjectedActorSupport
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

class EmailManagerSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with WordSpecLike with DefaultAwaitTimeout with FutureAwaits with MustMatchers
  with Mockito with ImplicitSender with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  object TestEmailActorFactory extends actors.ConfiguredEmailActor.Factory {
    def apply(): Actor = {
      val actorRef = TestActorRef[TestActor]
      actorRef.underlyingActor
    }
  }

  trait TestScope extends Scope {

    sealed trait TestInjectedActorSupport extends InjectedActorSupport {
      override def injectedChild(create: => Actor, name: String, props: Props => Props = identity)(implicit context: ActorContext): ActorRef = {
        TestActorRef[TestActor]
      }
    }

    val emailManager = TestActorRef(new EmailManager(TestEmailActorFactory) with TestInjectedActorSupport)
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "Email manager" should {

    "send email" in new TestScope {
      val request = EmailActor.SendEmail(List("test@example.com"), "test@example.com", "Hello World", "Hello World!")

      emailManager ! request

      expectMsg(request)
    }

    "send message to dead letters for unhandled message" in new TestScope {
      emailManager ! "blah!"

      expectNoMsg
    }

    "restart email manager" in new TestScope {
      val badRequest = EmailActor.SendEmail(List("test@example.com"), "test@example.com", "crash", "Hello World!")
      val request = EmailActor.SendEmail(List("test@example.com"), "test@example.com", "Hello World", "Hello World!")

      emailManager ! badRequest

      expectNoMsg

      emailManager ! request

      expectMsg(request)
    }

  }

}

class TestActor extends Actor {
  def receive: Receive = {
    case EmailActor.SendEmail(_, _, subject, _) if subject == "crash" => throw new EmailException
    case request: EmailActor.SendEmail                                => sender ! request
  }
}

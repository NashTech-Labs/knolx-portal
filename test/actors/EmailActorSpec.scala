package actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.mailer.{Email, MailerClient}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

class EmailActorSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with WordSpecLike with DefaultAwaitTimeout with FutureAwaits with MustMatchers
  with Mockito with ImplicitSender with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {
    val mailerClient = mock[MailerClient]

    val emailActor =
      TestActorRef(new EmailActor(mailerClient))
  }

  "Email Actor" should {

    "send email" in new TestScope {
      val email = Email("Hello World", "test@example.com", List("test@example.com"), bodyHtml = Some("Hello World!"))

      mailerClient.send(email) returns "emailId"

      emailActor ! EmailActor.SendEmail(List("test@example.com"), "test@example.com", "Hello World", "Hello World!")

      expectMsg(Some("emailId"))
    }

    "send message to dead letters for unhandled message" in new TestScope {
      emailActor ! "blah!"

      expectNoMsg
    }

  }

}

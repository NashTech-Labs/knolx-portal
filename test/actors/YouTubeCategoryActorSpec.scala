package actors

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.api.services.youtube.model.VideoCategory
import com.google.inject.name.Names
import controllers.TestEnvironment
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import services.YoutubeService


class YouTubeCategoryActorSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {

    val youtubeService = mock[YoutubeService]
    val youtubeCategoryActor =
      TestActorRef(new YouTubeCategoryActor(youtubeService))
  }

  "YouTube Category Actor" should {

    "return list of categories" in new TestScope {
      youtubeService.getCategoryList returns List(new VideoCategory().setId("12"))

      youtubeCategoryActor ! Categories

      expectMsgPF() {
        case listOfVideoCategory: List[VideoCategory] =>
          assert(listOfVideoCategory.head.getId == "12")
      }
    }
  }
}

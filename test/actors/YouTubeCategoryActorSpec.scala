package actors

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.api.services.youtube.model.VideoCategory
import com.google.inject.name.Names
import helpers.TestEnvironment
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import services.YoutubeService


class YouTubeDetailsActorSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {

    val youtubeService = mock[YoutubeService]
    val youtubeDetailsActor =
      TestActorRef(new YouTubeDetailsActor(youtubeService))
  }

  "YouTube Category Actor" should {

    "return list of categories" in new TestScope {
      youtubeService.getCategoryList returns List(new VideoCategory().setId("12"))

      youtubeDetailsActor ! Categories

      expectMsgPF() {
        case listOfVideoCategory: List[VideoCategory] =>
          assert(listOfVideoCategory.head.getId == "12")
      }
    }

    "update video details" in new TestScope {
      private val title = "title"
      private val description = Some("description")

      private val tags = List("tag1", "tag2")
      private val status = "public"
      private val category = "category"

      val videoDetails = VideoDetails("videoId",
        title,
        description,
        tags,
        status,
        category)

      youtubeService.update(videoDetails) returns "Successfully updated the video details"

      youtubeDetailsActor ! videoDetails

      expectMsg("Successfully updated the video details")
    }
  }
}

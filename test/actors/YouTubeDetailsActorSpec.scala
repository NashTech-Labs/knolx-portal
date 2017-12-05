package actors

import java.util

import actors.YouTubeDetailsActor.{GetCategories, GetDetails, UpdateVideoDetails}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model._
import com.google.inject.AbstractModule
import org.mockito.Mockito.when
import org.mockito.{Matchers, Mockito}
import org.scalatest.mock.MockitoSugar
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._


class YouTubeDetailsActorSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with MockitoSugar
  with SpecificationLike {

  def this() = this(ActorSystem("MySpec"))

  val titleOfVideo = "title"
  val description = "description"

  val tags = List("tag1", "tag2")
  val status = "public"
  val category = "category"

  trait TestScope extends Scope {
    val youtube = mock[YouTube](Mockito.RETURNS_DEEP_STUBS)

    val testModule = Option(new AbstractModule with AkkaGuiceSupport {
      override def configure(): Unit = {
        bind(classOf[YouTube]).toInstance(youtube)
      }
    })
  }

  "YouTube Details Actor" should {

    // =================================================================================================================
    // Unit tests
    // =================================================================================================================
    "return list of categories" in new TestScope {
      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube))

      val videoCategories = mock[YouTube#VideoCategories]
      val videoCategoriesList = mock[YouTube#VideoCategories#List]
      val videoCategoriesListRegion = mock[YouTube#VideoCategories#List]

      val videoCategoryListResponse = new VideoCategoryListResponse
      videoCategoryListResponse.setItems(new util.ArrayList[VideoCategory]())

      when(youtube.videoCategories()) thenReturn videoCategories
      when(videoCategories.list("snippet")) thenReturn videoCategoriesList
      when(videoCategoriesList.setRegionCode("IN")) thenReturn videoCategoriesListRegion
      when(videoCategoriesListRegion.execute()) thenReturn videoCategoryListResponse

      youtubeDetailsActor ! GetCategories

      expectMsg(List[VideoCategory]())
    }

    "update video details" in new TestScope {
      val videoSnippet = new VideoSnippet().setTitle(titleOfVideo).setDescription(description).setTags(tags.asJava)
      val video = new Video().setSnippet(videoSnippet).setStatus(new VideoStatus().setPrivacyStatus("private")).setId("videoId")
      val videoUpdate = mock[YouTube#Videos#Update]

      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube){
          override def getVideoSnippet(title: String,
                                       description: Option[String],
                                       tags: List[String],
                                       categoryId: String = ""): VideoSnippet = videoSnippet

          override def getVideo(snippet: VideoSnippet, status: String, videoId: String = ""): Video = video
        })

      val videoDetails = UpdateVideoDetails("videoId",
        titleOfVideo,
        Some(description),
        tags,
        status,
        category)

      when(youtube.videos().update("snippet,statistics,status", video)).thenReturn(videoUpdate)
      when(videoUpdate.execute()).thenReturn(video)

      youtubeDetailsActor ! videoDetails

      expectMsg(video)
    }

    "return details of a video" in new TestScope {
      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube))

      val videoListResponse = new VideoListResponse
      videoListResponse.setItems(List())

      when(youtube.videos().list("snippet,statistics,status").setId("videoId").execute) thenReturn videoListResponse

      youtubeDetailsActor ! GetDetails("videoId")

      expectMsg(None)
    }

    "return video snippet for no category id" in new TestScope {
      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube))

      val result = youtubeDetailsActor.underlyingActor.getVideoSnippet(titleOfVideo, Some(description), tags)

      result.getTitle must be equalTo titleOfVideo
    }

    "return video snippet when category id is not empty" in new TestScope {
      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube))

      val result = youtubeDetailsActor.underlyingActor.getVideoSnippet(titleOfVideo, Some(description), tags, "27")

      result.getTitle must be equalTo titleOfVideo
    }

    "return video for no videoId" in new TestScope {
      val videoSnippet = new VideoSnippet()

      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube))

      val result = youtubeDetailsActor.underlyingActor.getVideo(videoSnippet, "private")

      result.getStatus.getPrivacyStatus must be equalTo "private"
    }

    "return video for a given videoId" in new TestScope {
      val videoSnippet = new VideoSnippet()

      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube))

      val result = youtubeDetailsActor.underlyingActor.getVideo(videoSnippet, "private", "videoId")

      result.getStatus.getPrivacyStatus must be equalTo "private"
    }

    // =================================================================================================================
    // Integration tests
    // =================================================================================================================
    "update video details and return video" in new TestScope {
      val videoUpdate = mock[YouTube#Videos#Update]
      val video = new Video()

      val youtubeDetailsActor =
        TestActorRef(new YouTubeDetailsActor(youtube))

      val videoDetails = UpdateVideoDetails("videoId",
        titleOfVideo,
        Some(description),
        tags,
        status,
        category)

      when(youtube.videos().update(Matchers.any[String], Matchers.any[Video])) thenReturn videoUpdate
      when(videoUpdate.execute()) thenReturn video
      youtubeDetailsActor ! videoDetails

      expectMsg(video)
    }
  }
}

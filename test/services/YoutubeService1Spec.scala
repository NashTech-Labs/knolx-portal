package services

import java.io.InputStream
import java.util

import actors.VideoDetails
import akka.actor.{ActorSystem, ActorRef}
import akka.testkit.{ImplicitSender, TestKit}
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.{HttpRequestInitializer, AbstractInputStreamContent, InputStreamContent}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model._
import com.google.inject.name.Names
import helpers.TestEnvironment
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.specs2.mock.Mockito
import org.specs2.mutable.{SpecificationLike, Specification}
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{QualifierInstance, BindingKey}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import akka.testkit.{ImplicitSender, TestActors, TestKit}

class YoutubeService1Spec extends TestKit(ActorSystem("MySpec")) with SpecificationLike with ImplicitSender with MockitoSugar {

  val sessionId = "SessionId"
  val titleOfVideo = "title"
  val description = "Description"
  val tags = List("tags")

  "Youtube Service" should {

    "return list of categories" in {
      val youtube = mock[YouTube]
      val videoCategories = mock[YouTube#VideoCategories]
      val videoCategoriesList = mock[YouTube#VideoCategories#List]
      val videoCategoriesListRegion = mock[YouTube#VideoCategories#List]

      val service = new YoutubeService1(youtube)

      val videoCategoryListResponse = new VideoCategoryListResponse
      videoCategoryListResponse.setItems(List[VideoCategory]())

      when(youtube.videoCategories()) thenReturn videoCategories
      when(videoCategories.list("snippet")) thenReturn videoCategoriesList
      when(videoCategoriesList.setRegionCode("IN")) thenReturn videoCategoriesListRegion
      when(videoCategoriesListRegion.execute()) thenReturn videoCategoryListResponse

      val result = service.getCategoryList

      result must be equalTo List[VideoCategory]()
    }

    "return video after uploading" in {
      val fileSize = 10L

      val youtube = mock[YouTube](Mockito.RETURNS_DEEP_STUBS)
      val inputStream = mock[InputStream]
      val videoInsert = mock[YouTube#Videos#Insert]
      val abstractIS = mock[AbstractInputStreamContent]
      val httpReqInit = mock[HttpRequestInitializer]

      val videoSnippet = new VideoSnippet().setTitle(titleOfVideo).setDescription(description).setTags(tags.asJava)
      val video = new Video().setSnippet(videoSnippet).setStatus(new VideoStatus().setPrivacyStatus("private"))
      val inputStreamContent = new InputStreamContent("video/*", inputStream).setLength(fileSize)
      val netHttpTransport = new NetHttpTransport
      val uploader = new MediaHttpUploader(abstractIS, netHttpTransport, httpReqInit)

      val echo = system.actorOf(TestActors.echoActorProps)

      when(youtube.videos().insert("snippet,statistics,status", video, inputStreamContent)) thenReturn videoInsert

      val service =
        new YoutubeService1(youtube) {
          override def getVideoSnippet(title: String,
                                       description: Option[String],
                                       tags: List[String],
                                       categoryId: String = ""): VideoSnippet = videoSnippet

          override def getVideo(snippet: VideoSnippet, status: String, videoId: String = ""): Video = video

          override def getInputStreamContent(is: InputStream, fileSize: Long): InputStreamContent = inputStreamContent

          override def getMediaHttpUploader(videoInsert: YouTube#Videos#Insert, chunkSize: Int): MediaHttpUploader = uploader
        }

      when(videoInsert.execute()) thenReturn video

      val result =
        service
          .upload(sessionId,
            inputStream,
            titleOfVideo,
            Some(description),
            tags,
            fileSize,
            echo)

      result must be equalTo video
    }

    "update video details" in {
      val youtube = mock[YouTube](Mockito.RETURNS_DEEP_STUBS)
      val videoUpdate = mock[YouTube#Videos#Update]

      val videoDetails = VideoDetails("videoId", titleOfVideo, Some(description), tags, "public", "category")
      val videoSnippet = new VideoSnippet().setTitle(titleOfVideo).setDescription(description).setTags(tags.asJava)
      val video = new Video().setSnippet(videoSnippet).setStatus(new VideoStatus().setPrivacyStatus("private"))

      val service = new YoutubeService1(youtube) {
        override def getVideoSnippet(title: String,
                                     description: Option[String],
                                     tags: List[String],
                                     categoryId: String = ""): VideoSnippet = videoSnippet

        override def getVideo(snippet: VideoSnippet, status: String, videoId: String = ""): Video = video

      }

      when(youtube.videos().update("snippet,statistics,status", video)).thenReturn(videoUpdate)
      when(videoUpdate.execute()).thenReturn(video)

      val result = service.update(videoDetails)

      result must be equalTo video
    }

    "return video details" in {
      val youtube = mock[YouTube](Mockito.RETURNS_DEEP_STUBS)

      val service = new YoutubeService1(youtube)
      val videoListResponse = new VideoListResponse
      videoListResponse.setItems(List())

      when(youtube.videos().list("snippet,statistics,status").setId("videoId").execute) thenReturn videoListResponse

      val result = service.getVideoDetails("videoId")

      result must be equalTo List()
    }

    "return video snippet for no category id" in {
      val youtube = mock[YouTube](Mockito.RETURNS_DEEP_STUBS)
      val service = new YoutubeService1(youtube)

      val result = service.getVideoSnippet(titleOfVideo, Some(description), tags)

      result.getTitle must be equalTo titleOfVideo
    }

    "return video snippet when category id is not empty" in {
      val youtube = mock[YouTube]
      val service = new YoutubeService1(youtube)

      val result = service.getVideoSnippet(titleOfVideo, Some(description), tags, "27")

      result.getTitle must be equalTo titleOfVideo
    }

    "return video for no videoId" in {
      val youtube = mock[YouTube]
      val service = new YoutubeService1(youtube)
      val videoSnippet = new VideoSnippet()

      val result = service.getVideo(videoSnippet, "private")

      result.getStatus.getPrivacyStatus must be equalTo "private"
    }

    "return video for a given videoId" in {
      val youtube = mock[YouTube]
      val service = new YoutubeService1(youtube)
      val videoSnippet = new VideoSnippet()

      val result = service.getVideo(videoSnippet, "private", "videoId")

      result.getStatus.getPrivacyStatus must be equalTo "private"
    }

    "return input stream" in {
      val youtube = mock[YouTube]
      val service = new YoutubeService1(youtube)
      val inputStream = mock[InputStream]

      val result = service.getInputStreamContent(inputStream, 10L)

      result.getLength must be equalTo 10L
    }

    "return media http uploader" in {
      val youtube = mock[YouTube]
      val videoInsert = mock[YouTube#Videos#Insert]
      val abstractIS = mock[AbstractInputStreamContent]
      val httpReqInit = mock[HttpRequestInitializer]

      val netHttpTransport = new NetHttpTransport
      val mediaHttpUploader = new MediaHttpUploader(abstractIS, netHttpTransport, httpReqInit)
      mediaHttpUploader.setDirectUploadEnabled(false).setChunkSize(1024 * 0x400)
      val service = new YoutubeService1(youtube)

      when(videoInsert.getMediaHttpUploader) thenReturn mediaHttpUploader
      val result = service.getMediaHttpUploader(videoInsert, 1024 * 0x400)

      result.getChunkSize must be equalTo 1024 * 0x400
    }

  }

}

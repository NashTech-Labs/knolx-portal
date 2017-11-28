package services

import java.io.InputStream

import actors.VideoDetails
import akka.actor.ActorRef
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.{AbstractInputStreamContent, HttpRequestInitializer, InputStreamContent}
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoCategory, VideoSnippet}
import com.google.inject.name.Names
import helpers.TestEnvironment
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}

class YoutubeServiceSpec extends Specification {

  private val titleOfVideo = "title"
  private val description = "Description"
  private val tags = List("tags")

  abstract class WithTestApplication extends TestEnvironment with Scope {

    lazy val app: Application = fakeApp()

    val youtubeConfig = mock[YoutubeConfiguration]
    val credential = mock[Credential]
    val youtube = mock[YouTube]

    val inputStream = mock[InputStream]
    val videos = mock[YouTube#Videos]

    val sessionId = "SessionId"

    val netHttpTransport = new NetHttpTransport
    val jacksonFactory = new JacksonFactory
    val videoSnippet = new VideoSnippet

    val video = new Video()

    val youtubeUploadManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploadManager")))))

    lazy val service =
      new YoutubeService(youtubeUploadManager, youtubeConfig)
  }

  "Youtube Service" should {

    "return list of categories" in new WithTestApplication {
      val videoCategories = mock[YouTube#VideoCategories]
      val videoCategoriesList = mock[YouTube#VideoCategories#List]

      youtubeConfig.youtube returns youtube
      youtube.videoCategories() returns videoCategories
      videoCategories.list("snippet") returns videoCategoriesList

      videoCategoriesList.setRegionCode("IN") returns videoCategoriesList
      youtubeConfig.executeCategoryList(videoCategoriesList) returns List[VideoCategory]()

      val result = service.getCategoryList

      result must be equalTo List()
    }

    "return video after uploading" in new WithTestApplication {
      val fileSize = 10L
      val chunkSize = 256 * 0x400

      val isc = new InputStreamContent("", new InputStream {
        override def read(): Int = 1
      })
      val videoInsert = mock[YouTube#Videos#Insert]

      val abstractIS = mock[AbstractInputStreamContent]
      val httpReqInit = mock[HttpRequestInitializer]
      val mockActor = mock[ActorRef]

      val uploader = new MediaHttpUploader(abstractIS, netHttpTransport, httpReqInit)

      youtubeConfig.getVideoSnippet(titleOfVideo, Some(description), tags) returns videoSnippet
      youtubeConfig.getVideo(videoSnippet, "private") returns video
      youtubeConfig.getInputStreamContent(inputStream, fileSize) returns isc

      youtubeConfig.youtube returns youtube
      youtube.videos() returns videos
      youtubeConfig.part returns "part"

      videos.insert("part", video, isc) returns videoInsert
      youtubeConfig.getMediaHttpUploader(videoInsert, chunkSize) returns uploader
      videoInsert.execute() returns video

      val result =
        service
          .upload(sessionId,
            inputStream,
            titleOfVideo,
            Some(description),
            tags,
            fileSize,
            youtubeUploadManager)

      result must be equalTo video
    }

    "update video details" in new WithTestApplication {
      val videoUpdate = mock[YouTube#Videos#Update]
      val videoDetails = VideoDetails("videoId", titleOfVideo, Some(description), tags, "public", "category")

      youtubeConfig.getVideoSnippet(
        videoDetails.title,
        videoDetails.description,
        videoDetails.tags,
        videoDetails.category) returns videoSnippet

      youtubeConfig.getVideo(videoSnippet, videoDetails.status, videoDetails.videoId) returns video
      youtubeConfig.youtube returns youtube

      youtube.videos() returns videos
      youtubeConfig.part returns "part"
      videos.update("part", video) returns videoUpdate

      videoUpdate.execute() returns video

      val result = service.update(videoDetails)

      result must be equalTo "Successfully updated the video details"
    }

    "return video details" in new WithTestApplication {
      val listOfVideos = mock[YouTube#Videos#List]

      youtubeConfig.youtube returns youtube
      youtube.videos() returns videos
      youtubeConfig.part returns "part"

      videos.list("part") returns listOfVideos
      listOfVideos.setId("videoId") returns listOfVideos
      youtubeConfig.getVideoDetails(listOfVideos) returns List()

      val result = service.getVideoDetails("videoId")

      result must be equalTo None
    }
  }
}

package actors

import javax.inject.Inject

import akka.actor.Actor
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoCategory, VideoSnippet, VideoStatus}
import controllers.UpdateVideoDetails
import services.YoutubeService
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._


object ConfiguredYouTubeDetailsActor {

  trait Factory {
    def apply(): Actor
  }

}

case object GetCategories

case class UpdateVideoDetails(videoId: String,
                              title: String,
                              description: Option[String],
                              tags: List[String],
                              status: String,
                              category: String)

case class GetDetails(videoId: String)

class YouTubeDetailsActor @Inject()(youtube: YouTube) extends Actor {

  private val part = "snippet,statistics,status"

  override def receive: Receive = {
    case GetCategories                    => sender() ! returnCategoryList
    case videoDetails: UpdateVideoDetails => sender() ! update(videoDetails)
    case GetDetails(videoId: String)      => sender() ! getVideoDetails(videoId)
  }

  def returnCategoryList: List[VideoCategory] =
    youtube
      .videoCategories()
      .list("snippet")
      .setRegionCode("IN")
      .execute()
      .getItems
      .toList

  def update(videoDetails: UpdateVideoDetails): Video = {
    val snippet =
      getVideoSnippet(
        videoDetails.title,
        videoDetails.description,
        videoDetails.tags,
        videoDetails.category)

    val video = getVideo(snippet, videoDetails.status, videoDetails.videoId)

    val videoUpdate = youtube.videos().update(part, video)

    videoUpdate.execute()
  }

  def getVideoDetails(videoId: String): Option[UpdateVideoDetails] =
    youtube
      .videos()
      .list(part)
      .setId(videoId)
      .execute().getItems.toList.map { video =>
      val tags: List[String] = Option(video.getSnippet.getTags).fold[List[String]](Nil)(_.toList)
      UpdateVideoDetails(video.getSnippet.getTitle,
        Some(video.getSnippet.getDescription),
        tags,
        video.getStatus.getPrivacyStatus,
        video.getSnippet.getCategoryId)
    }.headOption

  def getVideoSnippet(title: String,
                      description: Option[String],
                      tags: List[String],
                      categoryId: String = ""): VideoSnippet = {
    val videoSnippet =
      new VideoSnippet()
        .setTitle(title)
        .setDescription(description.getOrElse(""))
        .setTags(tags.asJava)

    if (categoryId.isEmpty) videoSnippet else videoSnippet.setCategoryId(categoryId)
  }

  def getVideo(snippet: VideoSnippet, status: String, videoId: String = ""): Video = {
    val video =
      new Video()
        .setSnippet(snippet)
        .setStatus(new VideoStatus().setPrivacyStatus(status))

    if (videoId.isEmpty) video else video.setId(videoId)
  }
}

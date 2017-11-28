package actors

import javax.inject.Inject

import akka.actor.Actor
import com.google.api.services.youtube.model.VideoCategory
import controllers.UpdateVideoDetails
import services.YoutubeService


object ConfiguredYouTubeDetailsActor {

  trait Factory {
    def apply(): Actor
  }

}

case object GetCategories

case class VideoDetails(videoId: String,
                        title: String,
                        description: Option[String],
                        tags: List[String],
                        status: String,
                        category: String)

case class GetDetails(videoId: String)

class YouTubeDetailsActor @Inject()(youtubeService: YoutubeService) extends Actor {

  override def receive: Receive = {
    case GetCategories               => sender() ! returnCategoryList
    case videoDetails: VideoDetails  => sender() ! update(videoDetails)
    case GetDetails(videoId: String) => sender() ! getVideoDetails(videoId)
  }

  def returnCategoryList: List[VideoCategory] = youtubeService.getCategoryList

  def update(videoDetails: VideoDetails): String = youtubeService.update(videoDetails)

  def getVideoDetails(videoId: String): Option[UpdateVideoDetails] = youtubeService.getVideoDetails(videoId)
}

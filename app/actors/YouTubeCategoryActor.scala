package actors

import javax.inject.Inject

import akka.actor.Actor
import com.google.api.services.youtube.model.VideoCategory
import services.YoutubeService

import scala.collection.JavaConversions._


object ConfiguredYouTubeCategoryActor {

  trait Factory {
    def apply(): Actor
  }

}

case object Categories

class YouTubeCategoryActor @Inject()(youtubeService: YoutubeService) extends Actor {

  override def receive: Receive = {
    case Categories => sender() ! returnCategoryList
  }

  def returnCategoryList: List[VideoCategory] = {
    youtubeService.youtube.videoCategories().list("snippet").setRegionCode("IN").execute().getItems.toList
  }

}

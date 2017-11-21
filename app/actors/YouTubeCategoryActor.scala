package actors

import javax.inject.Inject

import akka.actor.Actor
import com.google.api.services.youtube.model.VideoCategory
import services.YoutubeService


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
    youtubeService.getCategoryList
  }

}

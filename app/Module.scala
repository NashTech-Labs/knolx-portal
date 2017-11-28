import java.util.function.Function

import actors._
import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.google.inject.util.Providers
import controllers.{DefaultKnolxControllerComponents, KnolxControllerComponents}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.libs.Akka

class Module extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bindActorFactory[EmailActor, ConfiguredEmailActor.Factory]
    bindActor[EmailManager]("EmailManager")

    bindActorFactory[YouTubeUploader, ConfiguredYouTubeUploader.Factory]
    bindActorFactory[YouTubeDetailsActor, ConfiguredYouTubeDetailsActor.Factory]

    bind[ActorRef]
      .annotatedWith(Names.named("SessionsScheduler"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[SessionsScheduler], "SessionsScheduler", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("UsersBanScheduler"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[UsersBanScheduler], "UsersBanScheduler", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeUploadManager"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeUploadManager], "YouTubeUploadManager", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeUploader"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeUploader], "YouTubeUploader", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeUploaderManager"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeUploaderManager], "YouTubeUploaderManager", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeDetailsActor"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeDetailsActor], "YouTubeDetailsActor", Function.identity())))
      .asEagerSingleton

    bind(classOf[KnolxControllerComponents])
      .to(classOf[DefaultKnolxControllerComponents])
      .asEagerSingleton()
  }

}

import java.util.function.Function

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.google.inject.util.Providers
import controllers.{DefaultKnolxControllerComponents, KnolxControllerComponents}
import net.codingwell.scalaguice.ScalaModule
import play.libs.Akka
import schedulers.SessionsScheduler

class Module extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[ActorRef]
      .annotatedWith(Names.named("SessionsScheduler"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[SessionsScheduler], "SessionsScheduler", Function.identity())))
      .asEagerSingleton

    bind(classOf[KnolxControllerComponents])
      .to(classOf[DefaultKnolxControllerComponents])
      .asEagerSingleton()
  }

}

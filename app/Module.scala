import java.util.function.Function
import javax.inject.{Inject, Provider}

import actors._
import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.google.inject.util.Providers
import controllers.{DefaultKnolxControllerComponents, KnolxControllerComponents}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.mailer._
import play.api.{Configuration, Environment}
import play.libs.Akka
import play.libs.mailer.{MailerClient => JMailerClient}

class Module(environment: Environment,
             configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bindActorFactory[EmailActor, ConfiguredEmailActor.Factory]
    //bindActor[EmailManager]("EmailManager")

    bind[ActorRef]
      .annotatedWith(Names.named("SessionsScheduler"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[SessionsScheduler], "SessionsScheduler", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("EmailManager"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[EmailManager], "EmailManager", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("UsersBanScheduler"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[UsersBanScheduler], "UsersBanScheduler", Function.identity())))
      .asEagerSingleton

    bind(classOf[KnolxControllerComponents])
      .to(classOf[DefaultKnolxControllerComponents])
      .asEagerSingleton()

    /*bind(classOf[MailerClient]).to(classOf[SMTPDynamicMailer])
    bind(classOf[JMailerClient]).to(classOf[MailerClient])
    bind(classOf[MailerClient]).annotatedWith(Names.named("mock")).to(classOf[MockMailer])
    bind(classOf[JMailerClient]).annotatedWith(Names.named("mock")).to(classOf[MockMailer])*/

    bind(classOf[MailerClient]).to(classOf[SMTPDynamicMailer]).asEagerSingleton()

    /*bind(classOf[MailerClient])
        .to(classOf[SMTPMailer])
      .asEagerSingleton()*/
  }

}

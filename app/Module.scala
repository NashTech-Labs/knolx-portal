import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport
import schedulers.SessionsScheduler

class Module extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bindActor[SessionsScheduler]("SessionsScheduler")
  }

}

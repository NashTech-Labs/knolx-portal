import com.google.inject.AbstractModule
import java.time.Clock

import net.codingwell.scalaguice.ScalaModule
import services.{ApplicationTimer, AtomicCounter, Counter}

class Module extends AbstractModule with ScalaModule {

  override def configure() = {
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
    bind(classOf[ApplicationTimer]).asEagerSingleton()
    bind(classOf[Counter]).to(classOf[AtomicCounter])
  }

}

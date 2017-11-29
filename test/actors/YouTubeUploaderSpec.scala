package actors

import java.io.InputStream

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.inject.name.Names
import helpers.TestEnvironment
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import services.YoutubeService

class YouTubeUploaderSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  private val sessionId = "SessionId"
  private val title = "title"
  private val description = Some("description")

  private val tags = List("tag1", "tag2")
  private val status = "public"
  private val category = "category"

  private val inputStream = new InputStream {
    override def read(): Int = 1
  }

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {
    lazy val app: Application = fakeApp()

    val youtubeProgressManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeProgressManager")))))

    val youtubeUploaderManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploaderManager")))))

    val youtubeService = mock[YoutubeService]
    val youtubeUploader =
      TestActorRef(new YouTubeUploader(youtubeProgressManager, youtubeUploaderManager, youtubeService))
  }

  "Youtube Uploader" should {

    "upload video" in new TestScope {

      youtubeUploader ! YouTubeUploader.Upload(sessionId, inputStream, title, description, tags, 1L)

      expectNoMsg
    }
  }
}

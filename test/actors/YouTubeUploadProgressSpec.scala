package actors

import akka.actor.{ActorRef, ActorSystem, Scheduler}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.google.inject.name.Names
import controllers.TestEnvironment
import models.{FeedbackFormsResponseRepository, SessionsRepository}
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.bson.BSONObjectID
import utilities.DateTimeUtility

/**
  * Created by knoldus on 16/11/17.
  */
class YouTubeUploadProgressSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with DefaultAwaitTimeout with FutureAwaits with ImplicitSender with TestEnvironment {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  trait TestScope extends Scope {

    lazy val app: Application = fakeApp()

    val sessionId: BSONObjectID = BSONObjectID.generate

    val youtubeUploadManager: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploadManager")))))

    val youtubeUploader: ActorRef =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("YouTubeUploader")))))

    val youtubeUploadProgress =
      TestActorRef(
        new YouTubeUploadProgress {
          override def preStart(): Unit = {}
        })
  }

  "Youtube Upload Progress" should {

    "return uploader for youtube video" in new TestScope {

      youtubeUploadProgress ! VideoUploader(sessionId.stringify)

      expectMsg(None)
    }
  }

}

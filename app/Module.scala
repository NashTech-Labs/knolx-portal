import java.io.{File, FileInputStream, InputStreamReader}
import java.util
import java.util.function.Function

import actors._
import akka.actor.ActorRef
import com.google.api.client.auth.oauth2.{Credential, StoredCredential, TokenResponse}
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.{DataStore, FileDataStoreFactory}
import com.google.api.services.youtube.YouTube
import com.google.common.collect.Lists
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.google.inject.util.Providers
import controllers.{DefaultKnolxControllerComponents, KnolxControllerComponents}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment}
import play.libs.Akka

class Module(environment: Environment,
             configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  private val httpTransport = new NetHttpTransport
  private val jsonFactory = new JacksonFactory

  private val youTubeCredentails = configuration.get[String]("youtube.credentials")
  private val clientSecretReader = new InputStreamReader(new FileInputStream(youTubeCredentails))
  private val clientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecretReader)

  private val credentialsDirectory = ".oauth-credentials"
  private val credentialDataStore = "uploadvideo"

  private val videoFileFormat = "video/*"
  val part = "snippet,statistics,status"

  private val scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload",
    "https://www.googleapis.com/auth/youtube")
  private val credential = authorize(scopes)
  private val youtube: YouTube =
    new YouTube.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("Knolx Portal")
      .build()

  override def configure(): Unit = {
    bindActorFactory[EmailActor, ConfiguredEmailActor.Factory]
    bindActor[EmailManager]("EmailManager")

    bind[ActorRef]
      .annotatedWith(Names.named("SessionsScheduler"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[SessionsScheduler], "SessionsScheduler", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("UsersBanScheduler"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[UsersBanScheduler], "UsersBanScheduler", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeManager"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeManager], "YouTubeManager", Function.identity())))
      .asEagerSingleton

    bindActorFactory[YouTubeUploader, ConfiguredYouTubeUploader.Factory]
    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeUploader"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeUploader], "YouTubeUploader", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeProgressManager"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeProgressManager], "YouTubeProgressManager", Function.identity())))
      .asEagerSingleton

    bindActorFactory[YouTubeDetailsActor, ConfiguredYouTubeDetailsActor.Factory]
    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeDetailsActor"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeDetailsActor], "YouTubeDetailsActor", Function.identity())))
      .asEagerSingleton

    bind(classOf[KnolxControllerComponents])
      .to(classOf[DefaultKnolxControllerComponents])
      .asEagerSingleton()

    bind[YouTube].toInstance(youtube)

  }

  private def authorize(scopes: util.List[String]): Credential = {
    val fileDataStoreFactory = new FileDataStoreFactory(new File(System.getProperty("user.home") + "/" + credentialsDirectory))
    val dataStore: DataStore[StoredCredential] = fileDataStoreFactory.getDataStore(credentialDataStore)

    val flow =
      new GoogleAuthorizationCodeFlow
      .Builder(httpTransport, jsonFactory, clientSecrets, scopes)
        .setCredentialDataStore(dataStore).build()

    val refreshToken = configuration.get[String]("youtube.refreshtoken")

    val response = new TokenResponse

    response.setRefreshToken(refreshToken)

    flow.createAndStoreCredential(response, "user")

  }

}

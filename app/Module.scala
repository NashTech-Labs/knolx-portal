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
import com.typesafe.config.ConfigFactory
import controllers.{DefaultKnolxControllerComponents, KnolxControllerComponents}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.libs.Akka
import services.YoutubeService

class Module extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  private val httpTransport = new NetHttpTransport
  private val jsonFactory = new JacksonFactory

  private val clientSecretReader = new InputStreamReader(new FileInputStream("/home/knoldus/Downloads/clients_secrets.json"))
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
      .annotatedWith(Names.named("YouTubeUploadManager"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeUploadManager], "YouTubeUploadManager", Function.identity())))
      .asEagerSingleton

    bindActorFactory[YouTubeUploader, ConfiguredYouTubeUploader.Factory]
    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeUploader"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeUploader], "YouTubeUploader", Function.identity())))
      .asEagerSingleton

    bind[ActorRef]
      .annotatedWith(Names.named("YouTubeUploaderManager"))
      .toProvider(Providers.guicify(Akka.providerOf(classOf[YouTubeUploaderManager], "YouTubeUploaderManager", Function.identity())))
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

    //bind[YoutubeService].toInstance(new YoutubeService)
  }

  private def authorize(scopes: util.List[String]): Credential = {
    val fileDataStoreFactory = new FileDataStoreFactory(new File(System.getProperty("user.home") + "/" + credentialsDirectory))
    val dataStore: DataStore[StoredCredential] = fileDataStoreFactory.getDataStore(credentialDataStore)

    val flow =
      new GoogleAuthorizationCodeFlow
      .Builder(httpTransport, jsonFactory, clientSecrets, scopes)
        .setCredentialDataStore(dataStore).build()

    val configFactory = ConfigFactory.load()

    val refreshToken = configFactory.getString("youtube.refreshToken")

    val response = new TokenResponse

    response.setRefreshToken(refreshToken)

    flow.createAndStoreCredential(response, "user1066")
  }

}

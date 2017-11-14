package actors

import java.io.{File, FileInputStream, InputStream, InputStreamReader}
import java.security.PrivateKey
import java.util
import javax.inject.{Inject, Named}

import actors.YouTubeUploader._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import com.google.api.client.auth.oauth2.{Credential, StoredCredential, TokenResponse}
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets, GoogleCredential}
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.{DataStore, FileDataStoreFactory}
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoSnippet, VideoStatus}
import com.google.common.collect.Lists
import com.typesafe.config.ConfigFactory
import play.api.Logger

import scala.collection.JavaConverters._
import scala.concurrent.Future

object YouTubeUploader {
  private val httpTransport = new NetHttpTransport
  private val jsonFactory = new JacksonFactory

  private val clientSecretReader = new InputStreamReader(new FileInputStream("/home/knoldus/Downloads/clients_secrets.json"))
  private val clientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecretReader)

  private val credentialsDirectory = ".oauth-credentials"
  private val credentialDataStore = "uploadvideo"

  private val videoFileFormat = "video/*"
  private val part = "snippet,statistics,status"
  private val status = new VideoStatus().setPrivacyStatus("private")

  private val scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload")
  private val credential = authorize(scopes)
  private val youtube =
    new YouTube.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("Knolx Portal")
      .build()

  /*private def authorize(scopes: util.List[String]): Credential = {
    val fileDataStoreFactory = new FileDataStoreFactory(new File(System.getProperty("user.home") + "/" + credentialsDirectory))
    val dataStore: DataStore[StoredCredential] = fileDataStoreFactory.getDataStore(credentialDataStore)

    val flow =
      new GoogleAuthorizationCodeFlow
      .Builder(httpTransport, jsonFactory, clientSecrets, scopes)
        .setCredentialDataStore(dataStore).build()

    /*val conf = ConfigFactory.load()

    val refreshToken = conf.getString("youtube.refreshToken")

    val response = new TokenResponse

    response.setRefreshToken(refreshToken)

    flow.createAndStoreCredential(response, "user")*/

    /*new GoogleCredential.Builder()
        .setTransport(httpTransport)
        .setJsonFactory(jsonFactory)
        .setServiceAccountId("test-knolx-portal@knolx-portal-test-account.iam.gserviceaccount.com")
        .setServiceAccountPrivateKeyFromP12File(new File("/home/knoldus/Downloads/Knolx Portal Test Account-cff9269f23ec.p12"))
        .setServiceAccountScopes(scopes)
        .setServiceAccountUser("isanythingallowed999@gmail.com")
        .build()*/

    /*new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(JSON_FACTORY)
      .setServiceAccountId(emailAddress)
      .setServiceAccountPrivateKeyFromP12File(new File("MyProject.p12"))
      .setServiceAccountScopes(Collections.singleton(SQLAdminScopes.SQLSERVICE_ADMIN))
      .setServiceAccountUser("user@example.com")
      .build()*/

    val localReceiver = new LocalServerReceiver.Builder().setPort(9001).build()

    new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user")
  }*/

  private def authorize(scopes: util.List[String]): Credential = {
    val fileDataStoreFactory = new FileDataStoreFactory(new File(System.getProperty("user.home") + "/" + credentialsDirectory))
    val dataStore: DataStore[StoredCredential] = fileDataStoreFactory.getDataStore(credentialDataStore)

    val flow =
      new GoogleAuthorizationCodeFlow
      .Builder(httpTransport, jsonFactory, clientSecrets, scopes)
          .setApprovalPrompt("force").setAccessType("offline")
        .setCredentialDataStore(dataStore).build()

    val localReceiver = new LocalServerReceiver.Builder().setPort(9001).build()

    new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user")
  }

  // Commands for YouTubeUploader actor
  case class Upload(sessionId: String,
                    is: InputStream,
                    title: String,
                    description: Option[String],
                    tags: List[String],
                    fileSize: Long)

  case class VideoId(sessionId: String)

}

class YouTubeUploader @Inject()(@Named("YouTubeUploadManager") youtubeUploaderManager: ActorRef,
                                @Named("YouTubeUploadProgress") youtubeUploadProgress: ActorRef) extends Actor {

  var videoCancelStatus: Map[String, Boolean] = Map.empty
  var videoIds: Map[String, String] = Map.empty

  def receive: Receive = {
    case YouTubeUploader.Upload(sessionId, is, title, description, tags, fileSize) => upload(sessionId, is, title, description, tags, fileSize)
    case YouTubeUploader.VideoId(sessionId) => sender() ! videoIds.get(sessionId)
    case msg                                                             =>
      Logger.info(s"Received a message in YouTubeUploader that cannot be handled $msg")
  }

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String],
             fileSize: Long): Video = {
    Logger.info(s"Starting video upload for session $sessionId")

    val snippet = new VideoSnippet().setTitle(title).setDescription(description.getOrElse("")).setTags(tags.asJava)
    val videoObjectDefiningMetadata = new Video().setSnippet(snippet).setStatus(status)
    val mediaContent = new InputStreamContent(videoFileFormat, is).setLength(fileSize)

    val videoInsert = youtube.videos().insert(part, videoObjectDefiningMetadata, mediaContent)
    val uploader = videoInsert.getMediaHttpUploader.setDirectUploadEnabled(false).setChunkSize(256 * 0x400)

    youtubeUploadProgress ! Uploader(sessionId, uploader)
    youtubeUploaderManager ! YouTubeUploadManager.RegisterUploadListener(sessionId, uploader)

    val video = videoInsert.execute()

    videoIds += sessionId -> video.getId

    video
  }

}

package actors

import java.io.{File, FileInputStream, InputStream, InputStreamReader}
import java.util
import javax.inject.{Inject, Named}

import actors.YouTubeUploader._
import akka.actor.{Actor, ActorRef}
import com.google.api.client.auth.oauth2.{Credential, StoredCredential, TokenResponse}
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
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

object YouTubeUploader {
  private val httpTransport = new NetHttpTransport
  private val jsonFactory = new JacksonFactory

  private val clientSecretReader = new InputStreamReader(new FileInputStream("/home/knoldus/Downloads/clients_secrets.json"))
  private val clientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecretReader)

  private val credentialsDirectory = ".oauth-credentials"
  private val credentialDataStore = "uploadvideo"

  private val videoFileFormat = "video/*"
  private val part = "snippet,statistics,status"
  private val status = new VideoStatus().setPrivacyStatus("public")

  private val scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload")
  private val credential = authorize(scopes)
  private val youtube =
    new YouTube.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("Knolx Portal")
      .build()

  private def authorize(scopes: util.List[String]): Credential = {
    val fileDataStoreFactory = new FileDataStoreFactory(new File(System.getProperty("user.home") + "/" + credentialsDirectory))
    val dataStore: DataStore[StoredCredential] = fileDataStoreFactory.getDataStore(credentialDataStore)

    val flow =
      new GoogleAuthorizationCodeFlow
      .Builder(httpTransport, jsonFactory, clientSecrets, scopes)
        .setAccessType("offline")
        .setCredentialDataStore(dataStore).build()

    val conf = ConfigFactory.load()

    val refreshToken = conf.getString("youtube.refreshToken")

    val response = new TokenResponse

    response.setRefreshToken(refreshToken)

    flow.createAndStoreCredential(response, "user")
  }

  // Commands for YouTubeUploader actor
  case class Upload(sessionId: String,
                    is: InputStream,
                    title: String,
                    description: Option[String],
                    tags: List[String])
}

class YouTubeUploader @Inject()(@Named("YouTubeUploadManager") youtubeUploaderManager: ActorRef) extends Actor {

  var videoCancelStatus: Map[String, Boolean] = Map.empty

  def receive: Receive = {
    case YouTubeUploader.Upload(sessionId, is, title, description, tags) => upload(sessionId, is, title, description, tags)
    case msg                                                             =>
      Logger.info(s"Received a message in YouTubeUploader that cannot be handled $msg")
  }

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String]): Video = {
    Logger.info(s"Starting video upload for session $sessionId")

    val snippet = new VideoSnippet().setTitle(title).setDescription(description.getOrElse("")).setTags(tags.asJava)
    val videoObjectDefiningMetadata = new Video().setSnippet(snippet).setStatus(status)
    val mediaContent = new InputStreamContent(videoFileFormat, is)

    val videoInsert = youtube.videos().insert(part, videoObjectDefiningMetadata, mediaContent)
    val uploader = videoInsert.getMediaHttpUploader.setDirectUploadEnabled(false)

    youtubeUploaderManager ! YouTubeUploadManager.RegisterUploadListener(sessionId, uploader)

    videoInsert.execute()
  }

}

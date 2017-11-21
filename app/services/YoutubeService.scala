package services

import java.io.{File, FileInputStream, InputStream, InputStreamReader}
import java.util
import javax.inject.{Inject, Named}

import actors.YouTubeUploadManager
import actors.YouTubeUploader.VideoDetails
import akka.actor.ActorRef
import com.google.api.client.auth.oauth2.{Credential, StoredCredential}
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.{DataStore, FileDataStoreFactory}
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.{Video, VideoCategory, VideoSnippet, VideoStatus}
import com.google.common.collect.Lists
import play.api.Logger

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

class YoutubeService @Inject()(
                                @Named("YouTubeUploadManager") youtubeUploadManager: ActorRef
                              ) {

  private val httpTransport = new NetHttpTransport
  private val jsonFactory = new JacksonFactory

  private val clientSecretReader = new InputStreamReader(new FileInputStream("/home/knoldus/Downloads/clients_secrets.json"))
  private val clientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecretReader)

  private val credentialsDirectory = ".oauth-credentials"
  private val credentialDataStore = "uploadvideo"

  private val videoFileFormat = "video/*"
  private val part = "snippet,statistics,status"
  private val status = "private"

  private val scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload",
    "https://www.googleapis.com/auth/youtube")
  private val credential = authorize(scopes)
  private val youtube =
    new YouTube.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("Knolx Portal")
      .build()

  private val chunkSize = 256 * 0x400

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
        .setCredentialDataStore(dataStore).build()

    /*val conf = ConfigFactory.load()

    val refreshToken = conf.getString("youtube.refreshToken")

    val response = new TokenResponse

    response.setRefreshToken(refreshToken)

    val a = flow.createAndStoreCredential(response, "user987")

    Logger.info("-----------------Access Token = " + a.getAccessToken)*/

    /*val localReceiver = new LocalServerReceiver.Builder().setPort(9001).build()

    new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user921")*/

    val localReceiver = new LocalServerReceiver.Builder().setPort(9001).build()

    new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user4093")

  }

  def getVideoSnippet: VideoSnippet = new VideoSnippet()

  def setSnippetDetails(snippet: VideoSnippet,
                        title: String,
                        description: Option[String],
                        tags: List[String],
                        categoryId: String = ""): VideoSnippet = {
    val constructedSnippet = snippet
      .setTitle(title)
      .setDescription(description.getOrElse(""))
      .setTags(tags.asJava)

    if(categoryId.isEmpty) constructedSnippet else constructedSnippet.setCategoryId(categoryId)
  }

  def getVideo: Video = new Video()

  def setVideoSnippet(video: Video, snippet: VideoSnippet): Video = video.setSnippet(snippet)

  def setVideoStatus(video: Video, status: String = status): Video = video.setStatus(new VideoStatus().setPrivacyStatus(status))

  def getInputStreamContent(is: InputStream, fileSize: Long): InputStreamContent =
    new InputStreamContent(videoFileFormat, is)
      .setLength(fileSize)

  def insertVideo(videoObjectDefiningMetadata: Video, mediaContent: InputStreamContent): YouTube#Videos#Insert =
    youtube
      .videos()
      .insert(part, videoObjectDefiningMetadata, mediaContent)

  def getUploader(videoInsert: YouTube#Videos#Insert): MediaHttpUploader =
    videoInsert.getMediaHttpUploader

  def setUploaderDetails(uploader: MediaHttpUploader): MediaHttpUploader =
    uploader
      .setDirectUploadEnabled(false)
      .setChunkSize(chunkSize)

  def executeInsert(videoInsert: YouTube#Videos#Insert): Video = videoInsert.execute()

  def executeUpdate(videoUpdate: YouTube#Videos#Update): Video = videoUpdate.execute()

  def setVideoId(video: Video, id: String): Video = video.setId(id)

  def updateVideo(video: Video): YouTube#Videos#Update =
    youtube
    .videos()
    .update(part, video)

  def getCategoryList: List[VideoCategory] =
    youtube
      .videoCategories()
      .list("snippet")
      .setRegionCode("IN")
      .execute()
      .getItems
      .toList

  def upload(sessionId: String,
             is: InputStream,
             title: String,
             description: Option[String],
             tags: List[String],
             fileSize: Long,
             sender: ActorRef): Video = {
    Logger.info(s"Starting video upload for session $sessionId")

    val snippet = new VideoSnippet().setTitle(title).setDescription(description.getOrElse("")).setTags(tags.asJava)
    val videoObjectDefiningMetadata = new Video().setSnippet(snippet).setStatus(new VideoStatus().setPrivacyStatus(status))
    val mediaContent = new InputStreamContent(videoFileFormat, is).setLength(fileSize)

    val videoInsert = youtube.videos().insert(part, videoObjectDefiningMetadata, mediaContent)
    val uploader = videoInsert.getMediaHttpUploader.setDirectUploadEnabled(false).setChunkSize(chunkSize)

    //youtubeUploadProgress ! Uploader(sessionId, uploader)
    youtubeUploadManager ! YouTubeUploadManager.RegisterUploadListener(sessionId, uploader)
    sender ! "Uploader set"

    val video = videoInsert.execute()

    //sessionVideos += sessionId -> video
    youtubeUploadManager ! YouTubeUploadManager.SessionVideo(sessionId, video)

    video
  }

  def update(videoDetails: VideoDetails): String = {

    val video = new Video

    val snippet = new VideoSnippet()
      .setTitle(videoDetails.title)
      .setDescription(videoDetails.description.getOrElse(""))
      .setTags(videoDetails.tags.asJava)
      .setCategoryId(videoDetails.category)
    val videoStatus = new VideoStatus().setPrivacyStatus(videoDetails.status)

    video.setSnippet(snippet).setStatus(videoStatus).setId(videoDetails.videoId)

    val videoUpdate = youtube.videos().update(part, video)
    try {
      videoUpdate.execute()
      "Successfully updated the video details"
    } catch {
      case error: Throwable =>
        "Something went wrong while updating the video details" + error + "-------------Video ID = " + videoDetails.videoId
    }
  }
}

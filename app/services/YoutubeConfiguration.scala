package services

import java.io.{File, FileInputStream, InputStream, InputStreamReader}
import java.util

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
import com.google.api.services.youtube.model._
import com.google.common.collect.Lists

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class YoutubeConfiguration {

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
  val youtube =
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

  def execute(a: YouTube#VideoCategories#List): List[VideoCategory] = {
    a.execute().getItems.toList
  }

  def getVideoSnippet(title: String,
                      description: Option[String],
                      tags: List[String],
                      categoryId: String = ""): VideoSnippet = {
    val videoSnippet =
      new VideoSnippet()
      .setTitle(title)
      .setDescription(description.getOrElse(""))
      .setTags(tags.asJava)

    if(categoryId.isEmpty) videoSnippet else videoSnippet.setCategoryId(categoryId)
  }

  def getVideo(snippet: VideoSnippet, status: String, videoId: String = ""): Video = {
    val video =
      new Video()
        .setSnippet(snippet)
        .setStatus(new VideoStatus().setPrivacyStatus(status))

    if(videoId.isEmpty) video else video.setId(videoId)
  }

  def getInputStreamContent(is: InputStream, fileSize: Long): InputStreamContent =
    new InputStreamContent(videoFileFormat, is).setLength(fileSize)

  def getMediaHttpUploader(videoInsert: YouTube#Videos#Insert, chunkSize: Int): MediaHttpUploader =
    videoInsert
      .getMediaHttpUploader
      .setDirectUploadEnabled(false)
      .setChunkSize(chunkSize)
}

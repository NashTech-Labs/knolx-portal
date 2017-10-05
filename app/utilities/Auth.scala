package utilities

import com.google.api.client.auth.oauth2.{Credential, StoredCredential}
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.DataStore
import com.google.api.client.util.store.FileDataStoreFactory
import java.io._
import java.util

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.http.javanet.NetHttpTransport

object Auth {

  val HTTP_TRANSPORT = new NetHttpTransport()

  val JSON_FACTORY = new JacksonFactory()

  val CREDENTIALS_DIRECTORY = ".oauth-credentials"

  def authorize(scopes : util.List[String], credentialDataStore:String): Credential = {

    val clientSecretReader = new InputStreamReader(new FileInputStream("conf/clients_secrets.json"))
    val clientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretReader)

    val fileDataStoreFactory: FileDataStoreFactory = new FileDataStoreFactory(new File(System.getProperty("user.home") + "/" + CREDENTIALS_DIRECTORY))
    val dataStore: DataStore[StoredCredential] = fileDataStoreFactory.getDataStore(credentialDataStore)

    val flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT,JSON_FACTORY,clientSecrets,scopes)
      .setCredentialDataStore(dataStore).build()

    // Build the local server and bind it to port 8080
    val localReceiver = new LocalServerReceiver.Builder().setPort(9001).build()

    // Authorize
    new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user")
  }
}
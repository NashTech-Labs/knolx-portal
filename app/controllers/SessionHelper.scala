package controllers

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.mvc.Request
import utilities.EncryptionUtility

object SessionHelper {

  val configuration = new Configuration(ConfigFactory.load("application.conf"))
  val username = configuration.get[String]("session.username")

  def email(implicit request: Request[_]): String =
    EncryptionUtility.decrypt(request.session.get(username).getOrElse(""))

  def isLoggedIn(implicit request: Request[_]): Boolean =
    request.session.get(username).isEmpty

  def isAdmin(implicit request: Request[_]): Boolean =
    EncryptionUtility.decrypt(request.session.get("admin").getOrElse("")) == EncryptionUtility.AdminKey

}

object EmailHelper {

  def isValidEmail(email: String): Boolean = """([\w\.]+)@knoldus\.(com|in)""".r.unapplySeq(email).isDefined

}

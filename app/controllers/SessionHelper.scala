package controllers

import play.api.mvc.{Security, Request}
import utilities.EncryptionUtility

object SessionHelper {

  def email(implicit request: Request[_]): String =
    EncryptionUtility.decrypt(request.session.get(Security.username).getOrElse(""))

  def isLoggedIn(implicit request: Request[_]): Boolean =
    request.session.get(Security.username).isEmpty

  def isAdmin(implicit request: Request[_]): Boolean =
    EncryptionUtility.decrypt(request.session.get("admin").getOrElse("")) == EncryptionUtility.AdminKey

}

object EmailHelper {

  def isValidEmail(email: String): Boolean = true//"""([\w\.]+)@knoldus\.(com|in)""".r.unapplySeq(email).isDefined

}

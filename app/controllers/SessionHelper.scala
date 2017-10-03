package controllers

import play.api.libs.typedmap.TypedKey
import play.api.mvc.Request
import utilities.EncryptionUtility

object SessionHelper {

  def email(implicit request: Request[_]): String =
    EncryptionUtility.decrypt(request.attrs.get(Attribute.UserName).getOrElse(""))

  def isLoggedIn(implicit request: Request[_]): Boolean =
    request.attrs.get(Attribute.UserName).isEmpty

  def isAdmin(implicit request: Request[_]): Boolean =
    EncryptionUtility.decrypt(request.session.get("admin").getOrElse("")) == EncryptionUtility.AdminKey

}

object EmailHelper {

  def isValidEmail(email: String): Boolean = """([\w\.]+)@knoldus\.(com|in)""".r.unapplySeq(email).isDefined

}

object Attribute {
  val UserName: TypedKey[String] = TypedKey("userName")
}
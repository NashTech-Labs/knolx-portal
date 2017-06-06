package controllers

import models.UsersRepository
import play.api.Logger
import play.api.mvc._
import utilities.EncryptionUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class UserSession(id: String, email: String, admin: Boolean)

case class SecuredRequest[A](user: UserSession, request: Request[A]) extends WrappedRequest(request)

trait SecuredImplicit {
  self: Controller =>

  val usersRepo: UsersRepository

  object UserAction extends ActionBuilder[SecuredRequest] {
    def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[Result]): Future[Result] = {
      implicit val req = request

      val emailFromSession = EncryptionUtility.decrypt(request2session.get(Security.username).getOrElse(""))

      usersRepo
        .getByEmail(emailFromSession)
        .flatMap(_.headOption.fold {
          Logger.info(s"Unauthorized access for email $emailFromSession")
          Future.successful(Unauthorized("Unauthorized access!"))
        } { userJson =>
          val email = userJson.fields.toMap.get("email").flatMap(_.validate[String].asOpt).getOrElse("")
          val admin = userJson.fields.toMap.get("admin").flatMap(_.validate[Boolean].asOpt).getOrElse(false)

          val userSession = UserSession("id", email, admin)
          block(SecuredRequest(userSession, request))
        })
    }
  }

  object AdminAction extends ActionBuilder[SecuredRequest] {
    def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[Result]): Future[Result] = {
      implicit val req = request

      val emailFromSession = EncryptionUtility.decrypt(request2session.get(Security.username).getOrElse(""))

      usersRepo
        .getByEmail(emailFromSession)
        .flatMap(_.headOption.fold {
          Logger.info(s"Unauthorized access for email $emailFromSession")
          Future.successful(Unauthorized("Unauthorized access!"))
        } { userJson =>
          val id = userJson.fields.toMap.get("_id").map(_.validate[Map[String, String]].get("$oid")).getOrElse("")
          val email = userJson.fields.toMap.get("email").flatMap(_.validate[String].asOpt).getOrElse("")
          val admin = userJson.fields.toMap.get("admin").flatMap(_.validate[Boolean].asOpt).getOrElse(false)

          if (admin) {
            val userSession = UserSession(id, email, admin)
            block(SecuredRequest(userSession, request))
          } else {
            Future.successful(Unauthorized("Unauthorized access!"))
          }
        })
    }
  }

}

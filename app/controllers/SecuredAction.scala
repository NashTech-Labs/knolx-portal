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
        } { userInfo =>
          val email = userInfo.email
          val admin = userInfo.admin
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
        } { userInfo =>
          val id = userInfo._id.stringify
          val email = userInfo.email
          val admin = userInfo.admin

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

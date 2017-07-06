package controllers

import javax.inject.Inject

import models.UsersRepository
import play.api.Logger
import play.api.http.Status._
import play.api.mvc._
import utilities.EncryptionUtility

import scala.concurrent.{ExecutionContext, Future}

case class UserSession(id: String, email: String, admin: Boolean)

case class SecuredRequest[A](user: UserSession, request: Request[A]) extends WrappedRequest(request)

class UserActionBuilder @Inject() (val parser: BodyParsers.Default, usersRepository: UsersRepository)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[SecuredRequest, AnyContent] {
  val unauthorized = new Results.Status(UNAUTHORIZED)

  def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[Result]): Future[Result] = {
    implicit val req = request

    val emailFromSession = EncryptionUtility.decrypt(request.session.get(Security.username).getOrElse(""))

    usersRepository
      .getByEmail(emailFromSession)
      .flatMap(_.headOption.fold {
        Logger.info(s"Unauthorized access for email $emailFromSession")

        Future.successful(unauthorized("Unauthorized access!"))
      } { userInfo =>
        val userSession = UserSession(userInfo._id.stringify, userInfo.email, userInfo.admin)

        block(SecuredRequest(userSession, request))
      })
  }

}

class AdminActionBuilder @Inject() (val parser: BodyParsers.Default, usersRepository: UsersRepository)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[SecuredRequest, AnyContent] {
  val unauthorized = new Results.Status(UNAUTHORIZED)

  def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[Result]): Future[Result] = {
    implicit val req = request

    val emailFromSession = EncryptionUtility.decrypt(request.session.get(Security.username).getOrElse(""))

    usersRepository
      .getByEmail(emailFromSession)
      .flatMap(_.headOption.fold {
        Logger.info(s"Unauthorized access for email $emailFromSession")
        Future.successful(unauthorized("Unauthorized access!"))
      } { userInfo =>
        if (userInfo.admin) {
          val userSession = UserSession(userInfo._id.stringify, userInfo.email, userInfo.admin)

          block(SecuredRequest(userSession, request))
        } else {
          Logger.info(s"Unauthorized access for email $emailFromSession")

          Future.successful(unauthorized("Unauthorized access!"))
        }
      })
  }

}
package controllers

import javax.inject.Inject

import models.UsersRepository
import play.api.http.Status._
import play.api.mvc._
import play.api.{Configuration, Logger}
import utilities.EncryptionUtility

import scala.concurrent.{ExecutionContext, Future}

case class UserSession(id: String, email: String, admin: Boolean)

case class SecuredRequest[A](user: UserSession, request: Request[A]) extends WrappedRequest(request)

case class UserActionBuilder(val parser: BodyParser[AnyContent],
                             usersRepository: UsersRepository,
                             configuration: Configuration
                            )(implicit val executionContext: ExecutionContext) extends ActionBuilder[SecuredRequest, AnyContent] {

  @Inject
  def this(parser: BodyParsers.Default, usersRepository: UsersRepository, configuration: Configuration)(implicit ec: ExecutionContext) =
    this(parser: BodyParser[AnyContent], usersRepository, configuration)

  val unauthorized = new Results.Status(UNAUTHORIZED)

  def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[Result]): Future[Result] = {
    implicit val req = request

    val username = configuration.get[String]("session.username")
    val emailFromSession = EncryptionUtility.decrypt(request.session.get(username).getOrElse(""))

    usersRepository
      .getByEmail(emailFromSession)
      .flatMap(_.fold {
        Logger.info(s"Unauthorized access for email $emailFromSession")

        Future.successful(unauthorized("Unauthorized access!"))
      } { userInfo =>
        val userSession = UserSession(userInfo._id.stringify, userInfo.email, userInfo.admin)

        block(SecuredRequest(userSession, request))
      })
  }

}

case class AdminActionBuilder(val parser: BodyParser[AnyContent],
                              usersRepository: UsersRepository,
                              configuration: Configuration
                             )(implicit val executionContext: ExecutionContext) extends ActionBuilder[SecuredRequest, AnyContent] {

  @Inject
  def this(parser: BodyParsers.Default, usersRepository: UsersRepository, configuration: Configuration)(implicit ec: ExecutionContext) =
    this(parser: BodyParser[AnyContent], usersRepository, configuration)

  val unauthorized = new Results.Status(UNAUTHORIZED)

  def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[Result]): Future[Result] = {
    implicit val req = request

    val username = configuration.get[String]("session.username")
    val emailFromSession = EncryptionUtility.decrypt(request.session.get(username).getOrElse(""))

    usersRepository
      .getByEmail(emailFromSession)
      .flatMap(_.fold {
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

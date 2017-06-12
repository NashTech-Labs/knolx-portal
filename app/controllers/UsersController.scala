package controllers

import javax.inject._

import controllers.UserFields._
import models.UsersRepository
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Controller, Security}
import utilities.{EncryptionUtility, PasswordUtility}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class UserInformation(email: String, password: String, confirmPassword: String)

case class LoginInformation(email: String, password: String)

object UserFields {
  val Email = "email"
  val Password = "password"
  val Algorithm = "algorithm"
  val Active = "active"
  val Admin = "admin"
}

@Singleton
class UsersController @Inject()(val messagesApi: MessagesApi, usersRepository: UsersRepository) extends Controller with I18nSupport {

  val userForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8),
      "confirmPassword" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8)
    )(UserInformation.apply)(UserInformation.unapply)
      verifying(
      "Password and confirm password do not match!",
      user => user.password.toLowerCase == user.confirmPassword.toLowerCase
      )
  )

  val loginForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText
    )(LoginInformation.apply)(LoginInformation.unapply)
  )

  def register: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.register(userForm))
  }

  def createUser: Action[AnyContent] = Action.async { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user registration $formWithErrors")
        Future.successful(BadRequest(views.html.register(formWithErrors)))
      },
      userInfo => {
        usersRepository
          .getByEmail(userInfo.email.toLowerCase)
          .flatMap(_.headOption.fold {
            usersRepository
              .insert(
                models.UserInfo(userInfo.email, PasswordUtility.encrypt(userInfo.password), PasswordUtility.BCrypt, active = true, admin = false)
              )
              .map { result =>
                if (result.ok) {
                  Logger.info(s"User ${userInfo.email} successfully created")
                  Redirect(routes.HomeController.index())
                    .withSession(Security.username -> EncryptionUtility.encrypt(userInfo.email.toLowerCase))
                } else {
                  Logger.error(s"Something went wrong while creating a new user ${userInfo.email}")
                  Redirect(routes.HomeController.index()).flashing("message" -> "Something went wrong!")
                }
              }
          } { user =>
            Logger.info(s"User with email ${userInfo.email.toLowerCase} already exists")
            Future.successful(Redirect(routes.UsersController.register()).flashing("message" -> "User already exists!"))
          })
      }
    )
  }

  def login: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.login(loginForm))
  }

  def loginUser: Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.login(formWithErrors)))
      },
      loginInfo => {
        usersRepository
          .getByEmail(loginInfo.email.toLowerCase)
          .map(_.headOption.fold {
            Logger.info(s"User ${loginInfo.email.toLowerCase} not found")
            Redirect(routes.HomeController.index()).flashing("message" -> "User not found!")
          } { user =>
            val admin = user.fields.toMap.get("admin").flatMap(_.validate[Boolean].asOpt).getOrElse(false)
            val password = user.fields.toMap.get(Password).flatMap(_.validate[String].asOpt).getOrElse("")

            if (PasswordUtility.isPasswordValid(loginInfo.password, password)) {
              Logger.info(s"User ${loginInfo.email.toLowerCase} successfully logged in")

              if (admin) {
                Redirect(routes.HomeController.index())
                  .withSession(
                    Security.username -> EncryptionUtility.encrypt(loginInfo.email.toLowerCase),
                    "admin" -> EncryptionUtility.encrypt(EncryptionUtility.AdminKey))
                  .flashing("message" -> "Welcome back!")
              } else {
                Redirect(routes.HomeController.index())
                  .withSession(Security.username -> EncryptionUtility.encrypt(loginInfo.email.toLowerCase))
                  .flashing("message" -> "Welcome back!")
              }
            } else {
              Logger.info(s"Incorrect password for user ${loginInfo.email}")
              Unauthorized(views.html.login(loginForm.fill(loginInfo).withGlobalError("Invalid credentials!")))
            }
          })
      }
    )
  }

  def logout: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.HomeController.index()).withNewSession
  }

}

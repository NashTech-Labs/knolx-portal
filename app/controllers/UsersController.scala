package controllers

import java.util.UUID
import javax.inject._

import models.{ForgotPasswordRepository, PasswordChangeRequestInfo, UpdatedUserInfo, UsersRepository}
import play.api.data.Forms.{nonEmptyText, _}
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONDateTime
import utilities.{DateTimeUtility, EncryptionUtility, PasswordUtility}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class UserInformation(email: String, password: String, confirmPassword: String)

case class ResetPasswordInformation(token: String,
                                    email: String,
                                    password: String,
                                    confirmPassword: String)

case class LoginInformation(email: String, password: String)

case class UserEmailInformation(email: Option[String], page: Int)

case class ManageUserInfo(email: String, active: Boolean, id: String)

case class UpdateUserInfo(email: String, active: Boolean, password: Option[String])

case class UserSearchResult(users: List[ManageUserInfo],
                            pages: Int,
                            page: Int,
                            keyword: String)

@Singleton
class UsersController @Inject()(messagesApi: MessagesApi,
                                usersRepository: UsersRepository,
                                forgotPasswordRepository: ForgotPasswordRepository,
                                configuration: Configuration,
                                dateTimeUtility: DateTimeUtility,
                                controllerComponents: KnolxControllerComponents,
                                mailerClient: MailerClient
                               ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val manageUserInfoFormat: OFormat[ManageUserInfo] = Json.format[ManageUserInfo]
  implicit val userSearchResultInfoFormat: OFormat[UserSearchResult] = Json.format[UserSearchResult]

  val userForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8),
      "confirmPassword" -> nonEmptyText.verifying("Confirm Password must be at least 8 character long!", password => password.length >= 8)
    )(UserInformation.apply)(UserInformation.unapply)
      verifying(
      "Password and confirm password miss match!", user => user.password.toLowerCase == user.confirmPassword.toLowerCase)
  )

  val resetPasswordForm = Form(
    mapping(
      "token" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8),
      "confirmPassword" -> nonEmptyText.verifying("Confirm Password must be at least 8 character long!", password => password.length >= 8)
    )(ResetPasswordInformation.apply)(ResetPasswordInformation.unapply)
      verifying(
      "Password and confirm password miss match!", user => user.password.toLowerCase == user.confirmPassword.toLowerCase)
  )

  val loginForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText
    )(LoginInformation.apply)(LoginInformation.unapply)
  )

  val emailForm = Form(
    mapping(
      "email" -> optional(nonEmptyText),
      "page" -> number.verifying("Invalid feedback form expiration days selected", number => number >= 0 && number <= 31)
    )(UserEmailInformation.apply)(UserEmailInformation.unapply)
  )

  val updateUserForm = Form(
    mapping(
      "email" -> email,
      "active" -> boolean,
      "password" -> optional(nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8))
    )(UpdateUserInfo.apply)(UpdateUserInfo.unapply)
  )

  val requestPasswordChangeForm = Form(
    single(
      "email" -> email
    )
  )

  def register: Action[AnyContent] = action { implicit request =>
    Ok(views.html.users.register(userForm))
  }

  def createUser: Action[AnyContent] = action.async { implicit request =>
    val username = configuration.get[String]("session.username")

    userForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user registration $formWithErrors")
        Future.successful(BadRequest(views.html.users.register(formWithErrors)))
      },
      userInfo => {
        usersRepository
          .getByEmail(userInfo.email.toLowerCase)
          .flatMap(_.fold {
            usersRepository
              .insert(
                models.UserInfo(userInfo.email, PasswordUtility.encrypt(userInfo.password), PasswordUtility.BCrypt, active = true, admin = false))
              .map { result =>
                if (result.ok) {
                  Logger.info(s"User ${userInfo.email} successfully created")
                  Redirect(routes.HomeController.index())
                    .withSession(username -> EncryptionUtility.encrypt(userInfo.email.toLowerCase))
                } else {
                  Logger.error(s"Something went wrong while creating a new user ${userInfo.email}")
                  Redirect(routes.HomeController.index()).flashing("message" -> "Something went wrong!")
                }
              }
          } { _ =>
            Logger.info(s"User with email ${userInfo.email.toLowerCase} already exists")
            Future.successful(Redirect(routes.UsersController.register()).flashing("message" -> "User already exists!"))
          })
      }
    )
  }

  def login: Action[AnyContent] = action { implicit request =>
    Ok(views.html.users.login(loginForm))
  }

  def loginUser: Action[AnyContent] = action.async { implicit request =>
    val username = configuration.get[String]("session.username")

    loginForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.users.login(formWithErrors)))
      },
      loginInfo => {
        usersRepository
          .getActiveByEmail(loginInfo.email.toLowerCase)
          .map(_.fold {
            Logger.info(s"User ${loginInfo.email.toLowerCase} not found")
            Redirect(routes.HomeController.index()).flashing("message" -> "User not found!")
          } { user =>
            val admin = user.admin
            val password = user.password

            if (PasswordUtility.isPasswordValid(loginInfo.password, password)) {
              Logger.info(s"User ${loginInfo.email.toLowerCase} successfully logged in")
              if (admin) {
                Redirect(routes.HomeController.index())
                  .withSession(
                    username -> EncryptionUtility.encrypt(loginInfo.email.toLowerCase),
                    "admin" -> EncryptionUtility.encrypt(EncryptionUtility.AdminKey))
                  .flashing("message" -> "Welcome back!")
              } else {
                Redirect(routes.HomeController.index())
                  .withSession(username -> EncryptionUtility.encrypt(loginInfo.email.toLowerCase))
                  .flashing("message" -> "Welcome back!")
              }
            } else {
              Logger.info(s"Incorrect password for user ${loginInfo.email}")
              Unauthorized(views.html.users.login(loginForm.fill(loginInfo).withGlobalError("Invalid credentials!")))
            }
          })
      }
    )
  }

  def logout: Action[AnyContent] = action { implicit request =>
    Redirect(routes.HomeController.index()).withNewSession
  }

  def manageUser(pageNumber: Int = 1, keyword: Option[String] = None): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository
      .paginate(pageNumber, keyword)
      .flatMap { userInfo =>
        val users = userInfo map (user =>
          ManageUserInfo(user.email, user.active, user._id.stringify))

        usersRepository
          .userCountWithKeyword(keyword)
          .map { count =>
            val pages = Math.ceil(count / 10D).toInt

            Ok(views.html.users.manageusers(users, pages, pageNumber, keyword))
          }
      }
  }

  def searchUser: Action[AnyContent] = adminAction.async { implicit request =>
    emailForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage ==> $formWithErrors")
        Future.successful(BadRequest(" OOps! Invalid value encountered !"))
      },
      userInformation => {
        usersRepository
          .paginate(userInformation.page, userInformation.email)
          .flatMap { userInfo =>
            val users = userInfo map (user => ManageUserInfo(user.email, user.active, user._id.stringify))

            usersRepository
              .userCountWithKeyword(userInformation.email)
              .map { count =>
                val pages = Math.ceil(count / 10D).toInt

                Ok(Json.toJson(UserSearchResult(users, pages, userInformation.page, userInformation.email.getOrElse(""))).toString)
              }
          }
      }
    )
  }

  def updateUser(): Action[AnyContent] = adminAction.async { implicit request =>
    updateUserForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage $formWithErrors")
        Future.successful(BadRequest(views.html.users.updateuser(formWithErrors)))
      },
      userInfo => {
        usersRepository.update(UpdatedUserInfo(userInfo.email, userInfo.active, userInfo.password))
          .flatMap { result =>
            if (result.ok) {
              Logger.info(s"User details successfully updated for ${userInfo.email}")
              Future.successful(Redirect(routes.UsersController.manageUser(1, None))
                .flashing("message" -> s"Details successfully updated for ${userInfo.email}"))
            } else {
              Future.successful(InternalServerError("Something went wrong!"))
            }
          }
      })
  }

  def getByEmail(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository
      .getByEmail(email)
      .flatMap {
        case Some(userInformation) =>
          val filledForm = updateUserForm.fill(UpdateUserInfo(userInformation.email, userInformation.active, None))
          Future.successful(Ok(views.html.users.updateuser(filledForm)))
        case None                  =>
          Future.successful(Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Something went wrong!"))
      }
  }

  def deleteUser(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository
      .delete(email)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete user with email $email")
        Future.successful(Redirect(routes.UsersController.manageUser(1, None)).flashing("errormessage" -> "Something went wrong!"))
      } { user =>
        Logger.info(s"user with email:  $email has been successfully deleted")
        Future.successful(Redirect(routes.UsersController.manageUser(1, None))
          .flashing("message" -> s"User with email ${user.email} has been successfully deleted!"))
      })
  }

  def forgotPassword: Action[AnyContent] = action.async { implicit request =>
    Future.successful(Ok(views.html.users.forgotpassword(requestPasswordChangeForm)))
  }

  def requestPasswordChange: Action[AnyContent] = action.async { implicit request =>
    requestPasswordChangeForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.users.forgotpassword(formWithErrors)))
      },
      email => {
        usersRepository
          .getActiveByEmail(email.toLowerCase).map { user =>
          user.fold {
            Redirect(routes.UsersController.forgotPassword()).flashing("message" -> "User not found, consider registering such user instead!")
          } { foundUser =>
            val validTill = dateTimeUtility.localDateTimeIST.plusDays(1)
            val token = UUID.randomUUID.toString
            val requestInfo = PasswordChangeRequestInfo(foundUser.email, token, BSONDateTime(dateTimeUtility.toMillis(validTill)))
            forgotPasswordRepository.upsert(requestInfo)

            val url = routes.UsersController.changePassword(token).url
            val changePasswordUrl = s"""http://${request.host}$url"""
            val email =
              Email(subject = s"Knolx portal password change request.",
                from = configuration.getString("play.mailer.user").getOrElse(""),
                to = List(foundUser.email),
                bodyHtml = Some(
                  s"""
                     |<p>Hi,</p>
                     |<p>Please click <a href="$changePasswordUrl">here</a> to reset your <strong>knolx portal</strong> password.</p></br></br>
                     |<strong><p>If you are not the one who has initiated this request kindly ignore this mail</p></strong>
                """.stripMargin),
                bodyText = None, replyTo = None)
            Try {
              mailerClient.send(email)
            } match {
              case Success(_) => Redirect(routes.UsersController.login())
                .flashing("successMessage" -> "An email with password reset link has been initiated to your registered account")
              case Failure(_) =>
                Redirect(routes.UsersController.login())
                  .flashing("message" -> "Sorry! Sending reset link email failed, please check your internet connection and try again!")
            }

          }
        }
      }
    )
  }

  def changePassword(token: String): Action[AnyContent] = action.async { implicit request =>
    forgotPasswordRepository.getPasswordChangeRequest(token, None).map { passwordChangeInfo =>
      passwordChangeInfo.fold {
        Unauthorized(views.html.users.login(loginForm.withGlobalError("Sorry, Your password change request is invalid or expired, consider generating new")))
      } { _ =>
        Ok(views.html.users.resetpassword(resetPasswordForm.fill(ResetPasswordInformation(token, "", "", ""))))
      }
    }
  }

  def resetPassword: Action[AnyContent] = action.async { implicit request =>
    resetPasswordForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for reseting password $formWithErrors")
        Future.successful(BadRequest(views.html.users.resetpassword(formWithErrors)))
      },
      resetPasswordInfo => {
        forgotPasswordRepository.getPasswordChangeRequest(resetPasswordInfo.token, Some(resetPasswordInfo.email.toLowerCase)).flatMap(resetRequest =>

          resetRequest.fold {
            Future.successful(Unauthorized(views.html.users.resetpassword(resetPasswordForm.fill(resetPasswordInfo)
              .withGlobalError("Sorry, no password reset request found for this user"))))
          } { requestFound =>
            usersRepository
              .getActiveByEmail(requestFound.email.toLowerCase).flatMap { user =>

              user.fold {
                Future.successful(Unauthorized(views.html.users.login(loginForm.withGlobalError("Sorry, No user found with email provided"))))
              } { userFound =>
                val updatedRecord = UpdatedUserInfo(userFound.email, userFound.active, Some(resetPasswordInfo.password))
                usersRepository.update(updatedRecord)
                  .map { result =>
                    if (result.ok) {
                      forgotPasswordRepository.upsert(requestFound.copy(active = false))
                      Logger.info(s"Password successfully updated for ${updatedRecord.email}")
                      Redirect(routes.UsersController.login())
                        .flashing("successMessage" -> s"Password successfully updated for ${updatedRecord.email}")
                    } else {
                      InternalServerError("Something went wrong!")
                    }
                  }
              }
            }
          }
        )
      })
  }

}

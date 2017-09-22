package controllers

import java.util.{Date, UUID}
import javax.inject._

import actors.EmailActor
import akka.actor.ActorRef
import controllers.EmailHelper._
import models.{ForgotPasswordRepository, PasswordChangeRequestInfo, UpdatedUserInfo, UsersRepository}
import play.api.data.Forms.{nonEmptyText, _}
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONDateTime
import utilities.{DateTimeUtility, EncryptionUtility, PasswordUtility}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class UserInformation(email: String, password: String, confirmPassword: String)

case class ResetPasswordInformation(token: String,
                                    email: String,
                                    password: String,
                                    confirmPassword: String)

case class ChangePasswordInformation(currentPassword: String, newPassword: String, confirmPassword: String)

case class LoginInformation(email: String, password: String)

case class SearchUserByEmailInformation(email: Option[String], page: Int, filter: String)

case class ManageUserInfo(email: String,
                          active: Boolean,
                          id: String,
                          banTill: String,
                          admin: Boolean = false,
                          ban: Boolean = false)

case class UpdateUserInfo(email: String,
                          active: Boolean,
                          ban: Boolean,
                          password: Option[String])

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
                                @Named("EmailManager") emailManager: ActorRef
                               ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val manageUserInfoFormat: OFormat[ManageUserInfo] = Json.format[ManageUserInfo]
  implicit val userSearchResultInfoFormat: OFormat[UserSearchResult] = Json.format[UserSearchResult]

  val userForm = Form(
    mapping(
      "email" -> email.verifying("Invalid Email", email => isValidEmail(email)),
      "password" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8),
      "confirmPassword" -> nonEmptyText.verifying("Confirm Password must be at least 8 character long!", password => password.length >= 8)
    )(UserInformation.apply)(UserInformation.unapply)
      verifying(
      "Password and confirm password miss match!", user => user.password.toLowerCase == user.confirmPassword.toLowerCase)
  )

  val forgotPasswordForm = Form(
    mapping(
      "token" -> nonEmptyText,
      "email" -> email.verifying("Invalid Email", email => isValidEmail(email)),
      "password" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8),
      "confirmPassword" -> nonEmptyText.verifying("Confirm Password must be at least 8 character long!", password => password.length >= 8)
    )(ResetPasswordInformation.apply)(ResetPasswordInformation.unapply)
      verifying(
      "Password and confirm password miss match!", user => user.password.toLowerCase == user.confirmPassword.toLowerCase)
  )

  val changePasswordForm = Form(
    mapping(
      "currentPassword" -> nonEmptyText,
      "newPassword" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8),
      "confirmPassword" -> nonEmptyText.verifying("Confirm Password must be at least 8 character long!", password => password.length >= 8)
    )(ChangePasswordInformation.apply)(ChangePasswordInformation.unapply)
      verifying(
      "Password and confirm password miss match!", user => user.newPassword.toLowerCase == user.confirmPassword.toLowerCase)
  )

  val loginForm = Form(
    mapping(
      "email" -> email.verifying("Invalid Email", email => isValidEmail(email)),
      "password" -> nonEmptyText
    )(LoginInformation.apply)(LoginInformation.unapply)
  )

  val searchUserByEmailForm = Form(
    mapping(
      "email" -> optional(nonEmptyText),
      "page" -> number.verifying("Invalid page number", number => number >= 0),
      "filter" -> nonEmptyText.verifying("Invalid filter",
        filter => filter == "all" || filter == "banned" || filter == "allowed" || filter == "active" || filter == "suspended")
    )(SearchUserByEmailInformation.apply)(SearchUserByEmailInformation.unapply)
  )

  val updateUserForm = Form(
    mapping(
      "email" -> email.verifying("Invalid Email", email => isValidEmail(email)),
      "active" -> boolean,
      "ban" -> boolean,
      "password" -> optional(nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8))
    )(UpdateUserInfo.apply)(UpdateUserInfo.unapply)
  )

  val requestPasswordChangeForm = Form(
    single(
      "email" -> email.verifying("Invalid Email", email => isValidEmail(email))
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
        val email = userInfo.email.toLowerCase.trim

        usersRepository
          .getByEmail(email)
          .flatMap(_.fold {
            usersRepository
              .insert(
                models.UserInfo(email,
                  PasswordUtility.encrypt(userInfo.password),
                  PasswordUtility.BCrypt,
                  active = true,
                  admin = false,
                  BSONDateTime(dateTimeUtility.nowMillis)))
              .map { result =>
                if (result.ok) {
                  Logger.info(s"User $email successfully created")
                  Redirect(routes.HomeController.index())
                    .withSession(username -> EncryptionUtility.encrypt(email))
                } else {
                  Logger.error(s"Something went wrong while creating a new user $email")
                  Redirect(routes.HomeController.index()).flashing("message" -> "Something went wrong!")
                }
              }
          } { _ =>
            Logger.info(s"User with email $email already exists")
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
        val email = loginInfo.email.toLowerCase.trim

        usersRepository
          .getActiveByEmail(email)
          .map(_.fold {
            Logger.info(s"User $email not found")
            Redirect(routes.HomeController.index()).flashing("message" -> "User not found!")
          } { user =>
            val admin = user.admin
            val password = user.password

            if (PasswordUtility.isPasswordValid(loginInfo.password, password)) {
              Logger.info(s"User $email successfully logged in")
              if (admin) {
                Redirect(routes.HomeController.index())
                  .withSession(
                    username -> EncryptionUtility.encrypt(email),
                    "admin" -> EncryptionUtility.encrypt(EncryptionUtility.AdminKey))
                  .flashing("message" -> "Welcome back!")
              } else {
                Redirect(routes.HomeController.index())
                  .withSession(username -> EncryptionUtility.encrypt(email))
                  .flashing("message" -> "Welcome back!")
              }
            } else {
              Logger.info(s"Incorrect password for user $email")
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
          ManageUserInfo(user.email,
            user.active,
            user._id.stringify,
            new Date(user.banTill.value).toString,
            user.admin,
            new Date(user.banTill.value).after(new Date(dateTimeUtility.nowMillis))))

        usersRepository
          .userCountWithKeyword(keyword)
          .map { count =>
            val pages = Math.ceil(count / 10D).toInt

            Ok(views.html.users.manageusers(users, pages, pageNumber, keyword))
          }
      }
  }

  def searchUser: Action[AnyContent] = adminAction.async { implicit request =>
    searchUserByEmailForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage ==> $formWithErrors")
        Future.successful(BadRequest(" OOps! Invalid value encountered !"))
      },
      userInformation => {
        usersRepository
          .paginate(userInformation.page, userInformation.email, userInformation.filter)
          .flatMap { userInfo =>
            val users = userInfo map (user =>
              ManageUserInfo(user.email,
                user.active,
                user._id.stringify,
                new Date(user.banTill.value).toString,
                user.admin,
                new Date(user.banTill.value).after(new Date(dateTimeUtility.nowMillis))))

            usersRepository
              .userCountWithKeyword(userInformation.email, userInformation.filter)
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
        usersRepository.update(UpdatedUserInfo(userInfo.email, userInfo.active, userInfo.ban, userInfo.password))
          .flatMap { result =>
            if (result.ok) {
              Logger.info(s"User details successfully updated for $email")
              Future.successful(Redirect(routes.UsersController.manageUser(1, None))
                .flashing("message" -> s"Details successfully updated for $email"))
            } else {
              Future.successful(InternalServerError("Something went wrong!"))
            }
          }
      })
  }

  def getByEmail(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository
      .getByEmail(email.toLowerCase.trim)
      .flatMap {
        case Some(userInformation) =>
          val ban = new Date(userInformation.banTill.value).after(new Date(dateTimeUtility.nowMillis))
          val filledForm = updateUserForm.fill(UpdateUserInfo(userInformation.email, userInformation.active, ban, None))
          Future.successful(Ok(views.html.users.updateuser(filledForm)))
        case None                  =>
          Future.successful(Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Something went wrong!"))
      }
  }

  def deleteUser(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    val cleanedEmail = email.toLowerCase.trim

    usersRepository
      .delete(cleanedEmail)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete user with email $cleanedEmail")
        Future.successful(Redirect(routes.UsersController.manageUser(1, None)).flashing("error" -> "Something went wrong!"))
      } { user =>
        Logger.info(s"user with email: $cleanedEmail has been successfully deleted")
        Future.successful(Redirect(routes.UsersController.manageUser(1, None))
          .flashing("success" -> s"User with email ${user.email} has been successfully deleted!"))
      })
  }

  def renderForgotPassword: Action[AnyContent] = action.async { implicit request =>
    Future.successful(Ok(views.html.users.forgotpassword(requestPasswordChangeForm)))
  }

  def generateForgotPasswordToken: Action[AnyContent] = action.async { implicit request =>
    requestPasswordChangeForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.users.forgotpassword(formWithErrors)))
      },
      email => {
        usersRepository
          .getActiveByEmail(email.toLowerCase.trim)
          .map { user =>
            user.fold {
              Redirect(routes.UsersController.renderForgotPassword())
                .flashing("message" -> "User not found!")
            } { foundUser =>
              val validTill = dateTimeUtility.localDateTimeIST.plusDays(1)
              val token = UUID.randomUUID.toString
              val requestInfo = PasswordChangeRequestInfo(foundUser.email, token, BSONDateTime(dateTimeUtility.toMillis(validTill)))
              forgotPasswordRepository.upsert(requestInfo)

              val url = routes.UsersController.validateForgotPasswordToken(token).url
              val changePasswordUrl = s"""http://${request.host}$url"""

              val from = configuration.getOptional[String]("play.mailer.user").getOrElse("")
              val subject = "Knolx portal password change request."
              val body =
                s"""<p>Hi,</p>
                   |<p>Please click <a href="$changePasswordUrl">here</a> to reset your <strong>knolx portal</strong> password.</p></br></br>
                   |<strong><p>If you are not the one who initiated this request kindly ignore this mail.</p></strong>
                """.stripMargin

              emailManager ! EmailActor.SendEmail(List(foundUser.email), from, subject, body)

              Redirect(routes.UsersController.login())
                .flashing("successMessage" -> "An email with password reset link has been sent to your registered account!")
            }
          }
      }
    )
  }

  def validateForgotPasswordToken(token: String): Action[AnyContent] = action.async { implicit request =>
    forgotPasswordRepository
      .getPasswordChangeRequest(token, None)
      .map { passwordChangeInfo =>
        passwordChangeInfo.fold {
          Unauthorized(views.html.users.login(loginForm.withGlobalError("Sorry, Your password change request is invalid " +
            "or expired, consider generating a new request!")))
        } { _ =>
          Ok(views.html.users.resetpassword(forgotPasswordForm.fill(ResetPasswordInformation(token, "", "", ""))))
        }
      }
  }

  def resetPassword: Action[AnyContent] = action.async { implicit request =>
    forgotPasswordForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for resetting password $formWithErrors")
        Future.successful(BadRequest(views.html.users.resetpassword(formWithErrors)))
      },
      resetPasswordInfo => {
        forgotPasswordRepository
          .getPasswordChangeRequest(resetPasswordInfo.token, Some(resetPasswordInfo.email.toLowerCase.trim))
          .flatMap(resetRequest =>
            resetRequest.fold {
              Future.successful(Unauthorized(views.html.users.resetpassword(forgotPasswordForm.fill(resetPasswordInfo)
                .withGlobalError("Sorry, no password reset request found for this user"))))
            } { requestFound =>
              usersRepository
                .getActiveByEmail(requestFound.email.toLowerCase.trim)
                .flatMap { user =>
                  user.fold {
                    Future.successful(Unauthorized(views.html.users.login(loginForm.withGlobalError("Sorry, No user found with email provided"))))
                  } { userFound =>
                    val ban = new Date(userFound.banTill.value).after(new Date(dateTimeUtility.nowMillis))
                    val updatedRecord = UpdatedUserInfo(userFound.email, userFound.active, ban, Some(resetPasswordInfo.password))

                    usersRepository
                      .updatePassword(updatedRecord)
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

  def renderChangePassword: Action[AnyContent] = userAction.async { implicit request =>
    Future.successful(Ok(views.html.users.changepassword(changePasswordForm
      .fill(ChangePasswordInformation("", "", "")))))
  }

  def changePassword: Action[AnyContent] = userAction.async { implicit request =>
    changePasswordForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for resetting password $formWithErrors")
        Future.successful(BadRequest(views.html.users.changepassword(formWithErrors)))
      },
      resetPasswordInfo => {
        val email = request.user.email.toLowerCase.trim

        usersRepository
          .getActiveByEmail(email)
          .flatMap(_.fold {
            Logger.info(s"User $email not found")
            Future.successful(Redirect(routes.UsersController.renderChangePassword()).flashing("message" -> "User not found!"))
          } { user =>
            if (PasswordUtility.isPasswordValid(resetPasswordInfo.currentPassword, user.password)) {
              val ban = new Date(user.banTill.value).after(new Date(dateTimeUtility.nowMillis))
              usersRepository.updatePassword(UpdatedUserInfo(request.user.email.toLowerCase, user.active, ban, Some(resetPasswordInfo.newPassword)))
                .map { result =>
                  if (result.ok) {
                    Logger.info(s"Password successfully updated for ${user.email}")
                    Redirect(routes.SessionsController.sessions(1, None)).flashing("message" -> "Password reset successfully!")
                  } else {
                    InternalServerError("Something went wrong!")
                  }
                }
            } else {
              Future.successful(Redirect(routes.UsersController.renderChangePassword()).flashing("message" -> "Current password invalid!"))
            }
          })
      })
  }

}

package controllers

import javax.inject.Inject

import models.UsersRepository
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, Controller}

class FeedbackController @Inject()(mailerClient: MailerClient,
                                   usersRepository: UsersRepository) extends Controller with SecuredImplicit {

  val usersRepo = usersRepository

  def feedbackForm: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.feedback.createfeedbackform())
  }

  def createFeedbackForm: Action[AnyContent] = Action { implicit request =>
    Ok
  }

  def sendFeedbackForm(sessionId: String): Action[AnyContent] = AdminAction { implicit request =>
    val email =
      Email(subject = "Knolx Feedback Form",
        from = "sidharth@knoldus.com",
        to = List("sidharth@knoldus.com"),
        bodyHtml = None,
        bodyText = Some("Hello World"), replyTo = None)

    val emailSent = mailerClient.send(email)

    Ok(emailSent)
  }
}

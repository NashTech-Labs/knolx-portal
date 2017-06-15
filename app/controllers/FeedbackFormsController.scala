package controllers

import javax.inject.Inject

import models.{Question, FeedbackForm, FeedbackFormsRepository, UsersRepository}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, Controller}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class FeedbackFormInformation(questions: List[QuestionInformation]) {

  def validateForm: Option[String] =
    if (questions.flatMap(_.options).nonEmpty) {
      None
    } else {
      Some("Question must require at least 1 option!")
    }

  def validateQuestion: Option[String] =
    if (!questions.map(_.question).contains("")) {
      None
    } else {
      Some("Question must not be empty!")
    }

  def validateOptions: Option[String] =
    if (!questions.flatMap(_.options).contains("")) {
      None
    } else {
      Some("Options must not be empty!")
    }

}

case class QuestionInformation(question: String, options: List[String])

class FeedbackFormsController @Inject()(mailerClient: MailerClient,
                                        usersRepository: UsersRepository,
                                        feedbackRepository: FeedbackFormsRepository) extends Controller with SecuredImplicit {

  implicit val feedbackFormInformationFormat = Json.format[QuestionInformation]

  val usersRepo = usersRepository

  def feedbackForm: Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.feedback.createfeedbackform())
  }

  def createFeedbackForm: Action[JsValue] = AdminAction.async(parse.json) { implicit request =>
    request.body.validate[List[QuestionInformation]].asOpt.fold {
      Logger.error(s"Received a bad request while creating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { questionsInformation =>
      val feedbackFormInformation = FeedbackFormInformation(questionsInformation)

      val formValid =
        feedbackFormInformation.validateForm orElse feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

      formValid.fold {
        val questions = questionsInformation.map(feedbackForm => Question(feedbackForm.question, feedbackForm.options))

        feedbackRepository.insert(FeedbackForm(questions)) map { result =>
          if (result.ok) {
            Logger.info(s"Feedback form successfully created")
            Ok("Feedback form successfully created!")
          } else {
            Logger.error(s"Something went wrong when creating a feedback")
            InternalServerError("Something went wrong!")
          }
        }
      } { errorMessage =>
        Logger.error(s"Received a bad request for feedback form, ${request.body} $errorMessage")
        Future.successful(BadRequest(errorMessage))
      }
    }
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

package controllers

import javax.inject.{Singleton, Inject}

import models.{Question, FeedbackForm, FeedbackFormsRepository, UsersRepository}
import play.api.Logger
import play.api.i18n.{MessagesApi, I18nSupport}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, Controller}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class QuestionInformation(question: String, options: List[String])

case class UpdateFeedbackFormInformation(id: String, name: String, questions: List[QuestionInformation])

case class FeedbackFormInformation(name: String, questions: List[QuestionInformation]) {

  def validateName: Option[String] =
    if (name.nonEmpty) {
      None
    } else {
      Some("Form name must not be empty!")
    }

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

@Singleton
class FeedbackFormsController @Inject()(val messagesApi: MessagesApi,
                                        mailerClient: MailerClient,
                                        usersRepository: UsersRepository,
                                        feedbackRepository: FeedbackFormsRepository) extends Controller with SecuredImplicit with I18nSupport {

  implicit val questionInformationFormat = Json.format[QuestionInformation]
  implicit val feedbackFormInformationFormat = Json.format[FeedbackFormInformation]

  val usersRepo = usersRepository

  def manageFeedbackForm(pageNumber: Int): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .paginate(pageNumber)
      .flatMap { feedbackForms =>
        val updateFormInformation = feedbackForms map { feedbackForm =>
          val questionInformation = feedbackForm.questions.map(question => QuestionInformation(question.question, question.options))

          UpdateFeedbackFormInformation(feedbackForm._id.stringify, feedbackForm.name, questionInformation)
        }

        feedbackRepository
          .activeCount
          .map { pages =>
            Ok(views.html.feedback.managefeedbackforms(updateFormInformation, pageNumber, pages))
          }
      }
  }

  def feedbackForm: Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.feedback.createfeedbackform())
  }

  def createFeedbackForm: Action[JsValue] = AdminAction.async(parse.json) { implicit request =>
    request.body.validate[FeedbackFormInformation].asOpt.fold {
      Logger.error(s"Received a bad request while creating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { feedbackFormInformation =>

      val formValid =
        feedbackFormInformation.validateForm orElse feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

      formValid.fold {
        val questions = feedbackFormInformation.questions.map(questionInformation => Question(questionInformation.question, questionInformation.options))

        feedbackRepository.insert(FeedbackForm(feedbackFormInformation.name, questions)) map { result =>
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

  def delete(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .delete(id)
      .map(_.fold {
        Logger.error(s"Could not delete feddback form for id, $id")
        Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("message" -> "Something went wrong!")
      }(_ =>
        Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("message" -> "Feedback form deleted successfully")))
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

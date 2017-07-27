package controllers

import javax.inject.{Inject, Singleton}

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class QuestionInformation(question: String, options: List[String], questionType: String, mandatory: Boolean)

case class FeedbackFormPreview(name: String, questions: List[QuestionInformation])

case class UpdateFeedbackFormInformation(id: String, name: String, questions: List[QuestionInformation]) {

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

  def validateType: Option[String] =
    if (questions.flatMap(_.questionType).contains("MCQ") || questions.flatMap(_.questionType).contains("COMMENT")) {
      None
    } else {
      Some("Server couldn't understand this request")
    }

  def validateMandatory: Option[String] =
    if (questions.map(_.mandatory) == true || questions.map(_.mandatory) == false) {
      None
    } else {
      Some("Server couldn't understand this request")
    }

}

@Singleton
class FeedbackFormsController @Inject()(messagesApi: MessagesApi,
                                        mailerClient: MailerClient,
                                        usersRepository: UsersRepository,
                                        feedbackRepository: FeedbackFormsRepository,
                                        sessionsRepository: SessionsRepository,
                                        dateTimeUtility: DateTimeUtility,
                                        controllerComponents: KnolxControllerComponents
                                       ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val questionInformationFormat: OFormat[QuestionInformation] = Json.format[QuestionInformation]
  implicit val feedbackFormInformationFormat: OFormat[FeedbackFormInformation] = Json.format[FeedbackFormInformation]
  implicit val feedbackPreviewFormat: OFormat[FeedbackFormPreview] = Json.format[FeedbackFormPreview]
  implicit val updateFeedbackFormInformationFormat: OFormat[UpdateFeedbackFormInformation] = Json.format[UpdateFeedbackFormInformation]

  def manageFeedbackForm(pageNumber: Int): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackRepository
      .paginate(pageNumber)
      .flatMap { feedbackForms =>
        val updateFormInformation = feedbackForms map { feedbackForm =>
          val questionInformation = feedbackForm.questions.map(question => QuestionInformation(question.question, question.options, question.questionType, question.mandatory))

          UpdateFeedbackFormInformation(feedbackForm._id.stringify, feedbackForm.name, questionInformation)
        }

        feedbackRepository
          .activeCount
          .map { pages =>
            Ok(views.html.feedbackforms.managefeedbackforms(updateFormInformation, pageNumber, pages))
          }
      }
  }

  def feedbackForm: Action[AnyContent] = adminAction { implicit request =>
    Ok(views.html.feedbackforms.createfeedbackform())
  }

  def createFeedbackForm: Action[JsValue] = adminAction.async(parse.json) { implicit request =>
    request.body.validate[FeedbackFormInformation].asOpt.fold {
      Logger.error(s"Received a bad request while creating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { feedbackFormInformation =>
      val formValid = feedbackFormInformation.validateName orElse feedbackFormInformation.validateForm orElse
        feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

      formValid.fold {
        val questions = feedbackFormInformation.questions.map(questionInformation => Question(questionInformation.question, questionInformation.options, questionInformation.questionType, questionInformation.mandatory))

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

  def getFeedbackFormPreview(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackRepository
      .getByFeedbackFormId(id)
      .map {
        case Some(feedbackForm) =>
          val questions = feedbackForm.questions map (question => QuestionInformation(question.question, question.options, question.questionType, question.mandatory))
          val feedbackPayload = FeedbackFormPreview(feedbackForm.name, questions)

          Ok(Json.toJson(feedbackPayload).toString)
        case None               => NotFound("404! feedback form not found")
      }
  }


  def update(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .activeSessions
      .flatMap { sessions =>
        if (sessions.foldLeft(false)(_ || _.feedbackFormId == id)) {
          Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1))
            .flashing("info" -> "Cannot edit feedback form as it has already been attached to a active session!"))
        } else {
          feedbackRepository
            .getByFeedbackFormId(id)
            .map {
              case Some(feedForm: FeedbackForm) =>
                Ok(views.html.feedbackforms.updatefeedbackform(feedForm, FeedbackFormsHelper.jsonCountBuilder(feedForm)))
              case None                         =>
                Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("message" -> "Something went wrong!")
            }
        }
      }
  }

  def updateFeedbackForm: Action[JsValue] = adminAction.async(parse.json) { implicit request =>
    request.body.validate[UpdateFeedbackFormInformation].asOpt.fold {
      Logger.error(s"Received a bad request while updating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { feedbackFormInformation =>
      sessionsRepository
        .activeSessions
        .flatMap { sessions =>
          if (sessions.foldLeft(false)(_ || _.feedbackFormId == feedbackFormInformation.id)) {
            Future.successful(
              Redirect(routes.FeedbackFormsController.manageFeedbackForm(1))
                .flashing("info" -> "Cannot edit feedback form as it has already been attached to a active session!"))
          } else {
            val validatedForm =
              feedbackFormInformation.validateForm orElse feedbackFormInformation.validateName orElse
                feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

            validatedForm.fold {
              val questions = feedbackFormInformation.questions.map(questionInformation => Question(questionInformation.question, questionInformation.options, questionInformation.questionType, questionInformation.mandatory))

              feedbackRepository.update(feedbackFormInformation.id, FeedbackForm(feedbackFormInformation.name, questions)) map { result =>
                if (result.ok) {
                  Logger.info(s"Feedback form successfully updated")
                  Ok("Feedback form successfully updated!")
                } else {
                  Logger.error(s"Something went wrong when updated a feedback")
                  InternalServerError("Something went wrong!")
                }
              }
            } { errorMessage =>
              Logger.error(s"Received a bad request for feedback form, ${request.body} $errorMessage")
              Future.successful(BadRequest(errorMessage))
            }
          }
        }
    }
  }

  def deleteFeedbackForm(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackRepository
      .delete(id)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete knolx feedback form with id $id")
        Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("errormessage" -> "Something went wrong!"))
      } { _ =>
        Logger.info(s"Knolx feedback form with id:  $id has been successfully deleted")
        Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("message" -> "Feedback form successfully deleted!"))
      })
  }

}

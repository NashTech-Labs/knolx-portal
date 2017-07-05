package controllers

import java.util.Date
import javax.inject.{Inject, Singleton}

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, Controller}
import reactivemongo.bson.BSONDateTime

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class QuestionInformation(question: String, options: List[String])

case class FeedbackFormPreview(name: String, questions: List[QuestionInformation])

case class QuestionAndResponseInformation(question: String, options: List[String], response: String)

case class FeedbackResponse(id: Option[String], feedBackFormId: String, sessionId: String, name: String,
                            questionsAndResponses: List[QuestionAndResponseInformation]) {

  def validateFeedFormId: Option[String] =
    if (feedBackFormId.nonEmpty) {
      None
    } else {
      Some("Feedback form id must not be empty!")
    }

  def validateSessionId: Option[String] =
    if (sessionId.nonEmpty) {
      None
    } else {
      Some("Session id must not be empty!")
    }

  def validateName: Option[String] =
    if (name.nonEmpty) {
      None
    } else {
      Some("Form name must not be empty!")
    }

  def validateForm: Option[String] =
    if (questionsAndResponses.flatMap(_.options).nonEmpty) {
      None
    } else {
      Some("Question must require at least 1 option!")
    }

  def validateQuestion: Option[String] =
    if (!questionsAndResponses.map(_.question).contains("")) {
      None
    } else {
      Some("Question must not be empty!")
    }

  def validateOptions: Option[String] =
    if (!questionsAndResponses.flatMap(_.options).contains("")) {
      None
    } else {
      Some("Options must not be empty!")
    }

  def validateFormResponse: Option[String] =
    if (questionsAndResponses.flatMap(_.response).nonEmpty) {
      None
    } else {
      Some("Response must not be empty!")
    }
}

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

}

@Singleton
class FeedbackFormsController @Inject()(val messagesApi: MessagesApi,
                                        mailerClient: MailerClient,
                                        usersRepository: UsersRepository,
                                        feedbackRepository: FeedbackFormsRepository,
                                        feedbackResponseRepository: FeedbackFormsResponseRepository) extends Controller with SecuredImplicit with I18nSupport {

  implicit val questionInformationFormat = Json.format[QuestionInformation]
  implicit val feedbackFormInformationFormat = Json.format[FeedbackFormInformation]
  implicit val feedbackPreviewFormat = Json.format[FeedbackFormPreview]
  implicit val updateFeedbackFormInformationFormat = Json.format[UpdateFeedbackFormInformation]
  implicit val questionAndReponseFormat = Json.format[QuestionAndResponseInformation]
  implicit val feedbackFormResponseFormat = Json.format[FeedbackResponse]

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
            Ok(views.html.feedbackforms.managefeedbackforms(updateFormInformation, pageNumber, pages))
          }
      }
  }

  def feedbackForm: Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.feedbackforms.createfeedbackform())
  }

  def createFeedbackForm: Action[JsValue] = AdminAction.async(parse.json) { implicit request =>
    request.body.validate[FeedbackFormInformation].asOpt.fold {
      Logger.error(s"Received a bad request while creating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { feedbackFormInformation =>
      val formValid = feedbackFormInformation.validateName orElse feedbackFormInformation.validateForm orElse
        feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

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

  def getFeedbackFormPreview(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .getByFeedbackFormId(id)
      .map {
        case Some(feedbackForm) =>
          val questions = feedbackForm.questions map (question => QuestionInformation(question.question, question.options))
          val feedbackPayload = FeedbackFormPreview(feedbackForm.name, questions)

          Ok(Json.toJson(feedbackPayload).toString)
        case None               => NotFound("404! feedback form not found")
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

  def update(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .getByFeedbackFormId(id)
      .map {
        case Some(feedForm: FeedbackForm) => Ok(views.html.feedbackforms.updatefeedbackform(feedForm, jsonCountBuilder(feedForm)))
        case None                         => Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Something went wrong!")
      }
  }

  def jsonCountBuilder(feedForm: FeedbackForm): String = {

    @tailrec
    def builder(questions: List[Question], json: List[String], count: Int): List[String] = {
      questions match {
        case Nil          => json
        case head :: tail => builder(tail, json :+ s""""$count":"${head.options.size}"""", count + 1)
      }
    }

    s"{${builder(feedForm.questions, Nil, 0).mkString(",")}}"
  }

  def updateFeedbackForm: Action[JsValue] = AdminAction.async(parse.json) { implicit request =>
    request.body.validate[UpdateFeedbackFormInformation].asOpt.fold {
      Logger.error(s"Received a bad request while updating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { feedbackFormInformation =>
      val validatedForm =
        feedbackFormInformation.validateForm orElse feedbackFormInformation.validateName orElse
          feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

      validatedForm.fold {
        val questions = feedbackFormInformation.questions.map(questionInformation => Question(questionInformation.question, questionInformation.options))

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

  def deleteFeedbackForm(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .delete(id)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete knolx feedback form with id $id")
        Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("errormessage" -> "Something went wrong!"))
      } { sessionJson =>
        Logger.info(s"Knolx feedback form with id:  $id has been successfully deleted")
        Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("message" -> "Feedback form successfully deleted!"))
      })
  }

  def storeFeedbackFormResponse: Action[JsValue] = UserAction.async(parse.json) { implicit request =>
    request.body.validate[FeedbackResponse].asOpt.fold {
      Logger.error(s"Received bad request while storing feedback response, ${request.body}")
      Future.successful(BadRequest("Malformed Data!"))
    } { feedbackFormResponse =>
      val validatedForm =
        feedbackFormResponse.validateFeedFormId orElse feedbackFormResponse.validateSessionId orElse
          feedbackFormResponse.validateName orElse feedbackFormResponse.validateForm orElse feedbackFormResponse.validateQuestion orElse
          feedbackFormResponse.validateOptions orElse feedbackFormResponse.validateFormResponse

      validatedForm.fold {
        val questionAndResponseInformation =
          feedbackFormResponse.questionsAndResponses.map(responseInformation =>
            QuestionResponse(responseInformation.question, responseInformation.options, responseInformation.response))

        val dateTime = new Date(System.currentTimeMillis).getTime

        val feedbackResponseData = FeedbackFormsResponse(request.user.id, request.user.email, feedbackFormResponse.sessionId,
          feedbackFormResponse.feedBackFormId, feedbackFormResponse.name, questionAndResponseInformation, BSONDateTime(dateTime))

        feedbackResponseRepository.insert(feedbackResponseData).map { result =>
          if (result.ok) {
            Logger.info(s"Feedback form response successfully stored")
            Ok("Feedback form response successfully strored!")
          } else {
            Logger.error(s"Something Went wrong when storing feddback form" +
              s" response ${feedbackFormResponse.feedBackFormId} for user ${request.user.email}")
            InternalServerError("Something Went Wrong!")
          }
        }
      } { errorMessage =>
        Logger.error(s"Received a bad request for feedback form, ${request.body} $errorMessage")
        Future.successful(BadRequest("Malformed Data!"))
      }

    }
  }
}

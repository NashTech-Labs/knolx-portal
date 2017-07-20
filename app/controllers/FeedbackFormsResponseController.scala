package controllers

import java.util.Date
import javax.inject.Inject

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class FeedbackSessions(userId: String,
                            email: String,
                            date: Date,
                            session: String,
                            feedbackFormId: String,
                            topic: String,
                            meetup: Boolean,
                            rating: String,
                            cancelled: Boolean,
                            active: Boolean,
                            id: String,
                            expirationDate: String)

case class FeedbackForms(name: String,
                         questions: List[QuestionInformation],
                         active: Boolean = true,
                         id: String)

case class QuestionAndResponseInformation(question: String, options: List[String], response: String)

case class FeedbackResponse(sessionId: String, questionsAndResponses: List[QuestionAndResponseInformation]) {

  def validateSessionId: Option[String] =
    if (sessionId.nonEmpty) {
      None
    } else {
      Some("Session id must not be empty!")
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

class FeedbackFormsResponseController @Inject()(messagesApi: MessagesApi,
                                                mailerClient: MailerClient,
                                                usersRepository: UsersRepository,
                                                feedbackRepository: FeedbackFormsRepository,
                                                feedbackResponseRepository: FeedbackFormsResponseRepository,
                                                sessionsRepository: SessionsRepository,
                                                dateTimeUtility: DateTimeUtility,
                                                controllerComponents: KnolxControllerComponents
                                               ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val questionInformationFormat: OFormat[QuestionInformation] = Json.format[QuestionInformation]
  implicit val FeedbackFormsFormat: OFormat[FeedbackForms] = Json.format[FeedbackForms]
  implicit val FeedbackResponseFormat: OFormat[FeedbackResponse] = Json.format[FeedbackResponse]
  implicit val QuestionAndResponseInformationFormat: OFormat[QuestionAndResponseInformation] = Json.format[QuestionAndResponseInformation]


  val usersRepo: UsersRepository = usersRepository

  def getFeedbackFormsForToday: Action[AnyContent] = userAction.async { implicit request =>
    sessionsRepository
      .activeSessions
      .flatMap { activeSessions =>
        if (activeSessions.nonEmpty) {
          val sessionFeedbackMappings = Future.sequence(activeSessions map { session =>
            feedbackRepository.getByFeedbackFormId(session.feedbackFormId) map {
              case Some(form) =>
                val sessionInformation =
                  FeedbackSessions(session.userId,
                    session.email,
                    new Date(session.date.value),
                    session.session,
                    session.feedbackFormId,
                    session.topic,
                    session.meetup,
                    session.rating,
                    session.cancelled,
                    session.active,
                    session._id.stringify,
                    new Date(session.expirationDate.value).toString)
                val questions = form.questions.map(questions => QuestionInformation(questions.question, questions.options))
                val associatedFeedbackFormInformation = FeedbackForms(form.name, questions, form.active, form._id.stringify)
                val feedbackFormQuestionOptionCount = FeedbackFormsHelper.jsonCountBuilder(form)

                Some((sessionInformation, Json.toJson(associatedFeedbackFormInformation).toString, feedbackFormQuestionOptionCount))
              case None =>
                Logger.info(s"No feedback form found correspond to feedback form id: ${session.feedbackFormId} for session id :${session._id}")
                None
            }
          })

          sessionFeedbackMappings.map(mappings => Ok(views.html.feedback.todaysfeedbacks(mappings.flatten, Nil)))
        } else {
          Logger.info("No active sessions found")
          immediatePreviousSessions.map(sessions => Ok(views.html.feedback.todaysfeedbacks(Nil, sessions)))
        }
      }
  }

  private def immediatePreviousSessions: Future[List[FeedbackSessions]] =
    sessionsRepository
      .immediatePreviousExpiredSessions
      .map { sessions =>
        sessions map (session =>
          FeedbackSessions(session.userId,
            session.email,
            new Date(session.date.value),
            session.session,
            session.feedbackFormId,
            session.topic,
            session.meetup,
            session.rating,
            session.cancelled,
            session.active,
            session._id.stringify,
            new Date(session.expirationDate.value).toString))
      }

  def storeFeedbackFormResponse: Action[JsValue] = userAction.async(parse.json) { implicit request =>
    request.body.validate[FeedbackResponse].asOpt.fold {
      Logger.error(s"Received bad request while storing feedback response, ${request.body}")
      Future.successful(BadRequest("Malformed Data!"))
    } { feedbackFormResponse =>
      val validatedForm =
        feedbackFormResponse.validateSessionId orElse
          feedbackFormResponse.validateForm orElse feedbackFormResponse.validateQuestion orElse
          feedbackFormResponse.validateOptions orElse feedbackFormResponse.validateFormResponse

      validatedForm.fold {
        val questionAndResponseInformation =
          feedbackFormResponse.questionsAndResponses.map(responseInformation =>
            QuestionResponse(responseInformation.question, responseInformation.options, responseInformation.response))

        val dateTime = new Date(System.currentTimeMillis).getTime

        val feedbackResponseData = FeedbackFormsResponse(request.user.id, request.user.email, feedbackFormResponse.sessionId,
          questionAndResponseInformation, BSONDateTime(dateTime))

        feedbackResponseRepository.insert(feedbackResponseData).map { result =>
          if (result.ok) {
            Logger.info(s"Feedback form response successfully stored")
            Ok("Feedback form response successfully strored!")
          } else {
            Logger.error(s"Something Went wrong when storing feedback form" +
              s" response feedback for  session ${feedbackFormResponse.sessionId} for user ${request.user.email}")
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

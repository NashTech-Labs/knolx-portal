package controllers

import java.util.Date
import javax.inject.Inject

import models._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
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

case class FeedbackResponse(sessionId: String, feedbackFormId: String, responses: List[String]) {

  def validateSessionId: Option[String] =
    if (sessionId.nonEmpty) {
      None
    } else {
      Some("Session id must not be empty!")
    }

  def validateFeedbackFormId: Option[String] =
    if (feedbackFormId.nonEmpty) {
      None
    } else {
      Some("feedback form id must not be empty!")
    }

  def validateFormResponse: Option[String] =
    if (responses.nonEmpty) {
      None
    } else {
      Some("Invalid form submitted")
    }
}

case class FetchedResponses(question: String, options: List[String], response: String, questionType: String, mandatory:Boolean)

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
  implicit val feedbackFormsFormat: OFormat[FeedbackForms] = Json.format[FeedbackForms]
  implicit val feedbackResponseFormat: OFormat[FeedbackResponse] = Json.format[FeedbackResponse]
  implicit val fetchedResponsesFormat: OFormat[FetchedResponses] = Json.format[FetchedResponses]



  val fetchFeedbackResponseForm = Form(
    single(
      "sessionId" -> nonEmptyText
    )
  )

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
                val questions = form.questions.map(questions => QuestionInformation(questions.question,
                  questions.options,
                  questions.questionType,
                  questions.mandatory))
                val associatedFeedbackFormInformation = FeedbackForms(form.name, questions, form.active, form._id.stringify)

                Some((sessionInformation, Json.toJson(associatedFeedbackFormInformation).toString))
              case None       =>
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

  def fetchFeedbackFormResponse: Action[AnyContent] = userAction.async{ implicit request =>
    fetchFeedbackResponseForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request while checking for responses ==> $formWithErrors")
        Future.successful(BadRequest(" OOps! Invalid value encountered !"))
      },
      sessionId => {
        feedbackResponseRepository.getByUsersSession(request.user.id,sessionId).map{ response =>
          response.fold{

          }{ (response: FeedbackFormsResponse) =>

            Ok()
          }

        }

        Future.successful(BadRequest("successo"))
      })
  }

  def storeFeedbackFormResponse: Action[JsValue] = userAction.async(parse.json) { implicit request =>
    request.body.validate[FeedbackResponse].asOpt.fold {
      Logger.error(s"Received bad request while storing feedback response, ${request.body}")
      Future.successful(BadRequest("Malformed Data!"))
    } { feedbackFormResponse =>

      val validatedForm =
        feedbackFormResponse.validateSessionId orElse
          feedbackFormResponse.validateFeedbackFormId orElse feedbackFormResponse.validateFormResponse
      validatedForm.fold {
        deepValidatedFeedbackResponses(feedbackFormResponse).flatMap { feedbackResponse =>
          feedbackResponse.fold {
            Future.successful(BadRequest("Malformed Data!"))
          } { response =>
            val timeStamp = dateTimeUtility.nowMillis
            val feedbackResponseData = FeedbackFormsResponse(request.user.email, request.user.id, feedbackFormResponse.sessionId,
              response, BSONDateTime(timeStamp))
            feedbackResponseRepository.upsert(feedbackResponseData).map { result =>
              if (result.ok) {
                Logger.info(s"Feedback form response successfully stored")
                Ok("Feedback form response successfully stored!")
              } else {
                Logger.error(s"Something Went wrong when storing feedback form" +
                  s" response feedback for  session ${feedbackFormResponse.sessionId} for user ${request.user.email}")
                InternalServerError("Something Went Wrong!")
              }
            }
          }
        }
      } { errorMessage =>
        Logger.error(s"Received a bad request for feedback form, ${request.body} $errorMessage")
        Future.successful(BadRequest("Malformed Data!"))
      }

    }
  }

  private def deepValidatedFeedbackResponses(userResponse: FeedbackResponse): Future[Option[List[QuestionResponse]]] = {
    sessionsRepository.getActiveById(userResponse.sessionId).flatMap { session =>
      session.fold {
        val badResponse: Option[List[QuestionResponse]] = None
        Future.successful(badResponse)
      } { _ =>
        feedbackRepository.getByFeedbackFormId(userResponse.feedbackFormId).map {
          case Some(feedbackForm) =>
            val questions = feedbackForm.questions
            if (questions.size == userResponse.responses.size) {
              val sanitizedResponses = sanitizeResponses(questions, userResponse.responses).toList.flatten
              if (questions.size == sanitizedResponses.size) {
                Some(sanitizedResponses)
              } else {
                None
              }
            } else {
              None
            }
          case None               => None
        }
      }
    }
  }

  private def sanitizeResponses(questions: Seq[Question], responses: List[String]): Seq[Option[QuestionResponse]] = {
    for ((question, response) <- questions zip responses) yield {

      (question.questionType, question.mandatory) match {
        case ("MCQ", true)      => if (question.options.contains(response) && response.nonEmpty) {
          Some(QuestionResponse(question.question, question.options, response))
        }
        else {
          None
        }
        case ("COMMENT", true)  => if (response.nonEmpty) {
          Some(QuestionResponse(question.question, question.options, response))
        } else {
          None
        }
        case ("MCQ", false)     => None
        case ("COMMENT", false) => Some(QuestionResponse(question.question, question.options, response))
        case _                  => None

      }
    }
  }

}

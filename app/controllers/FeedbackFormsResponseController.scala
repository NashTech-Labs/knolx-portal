package controllers

import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.{Inject, Named}

import actors.EmailActor
import akka.actor.ActorRef
import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent, Result}
import play.api.{Configuration, Logger}
import reactivemongo.api.commands.WriteResult
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
                            expirationDate: String,
                            feedbackSubmitted: Boolean = false)

case class FeedbackForms(name: String,
                         questions: List[QuestionInformation],
                         active: Boolean = true,
                         id: String)

case class FeedbackResponse(sessionId: String, feedbackFormId: String, responses: List[String], score: Double) {

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

case class ResponseHeader(topic: String,
                          email: String,
                          date: BSONDateTime,
                          session: String,
                          meetUp: Boolean)

case class BannedUser(bannedDaysLeft: Long, bannedTill: String)

class FeedbackFormsResponseController @Inject()(messagesApi: MessagesApi,
                                                mailerClient: MailerClient,
                                                usersRepository: UsersRepository,
                                                feedbackRepository: FeedbackFormsRepository,
                                                feedbackResponseRepository: FeedbackFormsResponseRepository,
                                                sessionsRepository: SessionsRepository,
                                                configuration: Configuration,
                                                @Named("EmailManager") emailManager: ActorRef,
                                                dateTimeUtility: DateTimeUtility,
                                                controllerComponents: KnolxControllerComponents
                                               ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  lazy val fromEmail: String = configuration.getOptional[String]("play.mailer.user").getOrElse("support@knoldus.com")
  lazy val host: String = configuration.getOptional[String]("knolx.url").getOrElse("localhost:9000")

  implicit val questionInformationFormat: OFormat[QuestionInformation] = Json.format[QuestionInformation]
  implicit val feedbackFormsFormat: OFormat[FeedbackForms] = Json.format[FeedbackForms]
  implicit val feedbackResponseFormat: OFormat[FeedbackResponse] = Json.format[FeedbackResponse]

  def getFeedbackFormsForToday: Action[AnyContent] = userAction.async { implicit request =>
    usersRepository.getActiveAndBanned(request.user.email.toLowerCase).flatMap {
      _.fold {
        sessionsRepository
          .activeSessions()
          .flatMap { activeSessions =>
            if (activeSessions.nonEmpty) {
              val sessionFeedbackMappings = Future.sequence(activeSessions filterNot { session => session.email == request.user.email.toLowerCase }
                map { session =>
                feedbackRepository.getByFeedbackFormId(session.feedbackFormId) flatMap {
                  case Some(form) =>
                    feedbackResponseRepository.getByUsersSession(request.user.id, session._id.stringify) map { response: Option[FeedbackFormsResponse] =>
                      val sessionInformation =
                        FeedbackSessions(session.userId, session.email, new Date(session.date.value), session.session, session.feedbackFormId,
                          session.topic, session.meetup, session.rating, session.cancelled, session.active, session._id.stringify,
                          new Date(session.expirationDate.value).toString, response.isDefined)

                      val questions = form.questions.map(questions => QuestionInformation(questions.question, questions.options,
                        questions.questionType, questions.mandatory))

                      val associatedFeedbackFormInformation = FeedbackForms(form.name, questions, form.active, form._id.stringify)

                      Some((sessionInformation, Json.toJson(associatedFeedbackFormInformation).toString))
                    }
                  case None       =>
                    Logger.info(s"No feedback form found correspond to feedback form id: ${session.feedbackFormId} for session id :${session._id}")
                    Future.successful(None)
                }
              })

              sessionFeedbackMappings.map(mappings => Ok(views.html.feedback.todaysfeedbacks(mappings.flatten, Nil)))
            } else {
              Logger.info("No active sessions found")
              immediatePreviousSessions.map(sessions => Ok(views.html.feedback.todaysfeedbacks(Nil, sessions)))
            }
          }
      } { bannedUser =>
        val bantill = dateTimeUtility.toLocalDate(bannedUser.banTill.value)
        val today = dateTimeUtility.localDateIST
        val daysLeft = today.until(bantill, ChronoUnit.DAYS)
        Future.successful(Unauthorized(views.html.feedback.banned(BannedUser(daysLeft, new Date(bannedUser.banTill.value).toString))))
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

  def fetchFeedbackFormResponse(sessionId: String): Action[AnyContent] = userAction.async { implicit request =>
    feedbackResponseRepository.getByUsersSession(request.user.id, sessionId).map { response =>
      response.fold {
        NotFound("fresh feedback")
      } { (response: FeedbackFormsResponse) =>
        val allResponses = response.feedbackResponse.map(responseInfo => responseInfo.response)
        Ok(Json.toJson(allResponses).toString())
      }
    }
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
            Logger.info(s"Feedback form submission unsuccessful due to Malformed data while " +
              s"validating form responses for session ${feedbackFormResponse.sessionId} for user ${request.user.email}")
            Future.successful(BadRequest("Malformed Data!"))
          } { sanitizedResponse =>
            val (header, response) = sanitizedResponse
            usersRepository.getByEmail(request.user.email).flatMap {
              _.fold {
                Logger.info(s"User ${request.user.email} not found")
                Future.successful(Redirect(routes.UsersController.login()).flashing("message" -> "User not found!"))
              } { userInfo =>
                val feedbackResponseData = FeedbackFormsResponse(request.user.email, userInfo.coreMember, header.email, request.user.id,
                  feedbackFormResponse.sessionId, header.topic, header.meetUp, header.date,
                  header.session, response, BSONDateTime(dateTimeUtility.nowMillis), feedbackFormResponse.score)
                feedbackResponseRepository.upsert(feedbackResponseData).flatMap { result =>
                  updateRatingIfCoreMember(result, request, feedbackFormResponse, userInfo, header)
                }
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

  private def updateRatingIfCoreMember(result: WriteResult,
                                       request: SecuredRequest[JsValue],
                                       feedbackFormResponse: FeedbackResponse,
                                       userInfo: UserInfo,
                                       header: ResponseHeader): Future[Result] = {
    if (userInfo.coreMember) {
      Logger.info(s"Feedback form response successfully stored for session ${feedbackFormResponse.sessionId} for user ${request.user.email}")
      feedbackResponseRepository.getScoresOfMembers(feedbackFormResponse.sessionId, isCoreMember = true).flatMap { scores =>
        sessionsRepository.updateRating(feedbackFormResponse.sessionId, scores).map { result =>
          if (result.ok) {
            emailManager ! EmailActor.SendEmail(
              List(request.user.email), fromEmail, "Feedback Successfully Registered!",
              views.html.emails.feedbackresponse(header.email, header.topic, header.meetUp).toString)
            Ok("Feedback form response successfully stored!")
          } else {
            Logger.error(s"Something Went wrong when storing feedback form" +
              s" response feedback for  session ${feedbackFormResponse.sessionId} for user ${request.user.email}")
            InternalServerError("Something Went Wrong!")
          }
        }
      }
    } else if (result.ok) {
      emailManager ! EmailActor.SendEmail(
        List(request.user.email), fromEmail, "Feedback Successfully Registered!",
        views.html.emails.feedbackresponse(header.email, header.topic, header.meetUp).toString)
      Future.successful(Ok("Feedback form response successfully stored!"))
    } else {
      Logger.error(s"Something Went wrong when storing feedback form" +
        s" response feedback for  session ${feedbackFormResponse.sessionId} for user ${request.user.email}")
      Future.successful(InternalServerError("Something Went Wrong!"))
    }
  }

  private def deepValidatedFeedbackResponses(userResponse: FeedbackResponse): Future[Option[(ResponseHeader, List[QuestionResponse])]] = {
    sessionsRepository.getActiveById(userResponse.sessionId).flatMap { session =>
      session.fold {
        val badResponse: Option[(ResponseHeader, List[QuestionResponse])] = None
        Future.successful(badResponse)
      } { session =>
        feedbackRepository.getByFeedbackFormId(userResponse.feedbackFormId).map {
          case Some(feedbackForm) =>
            val questions = feedbackForm.questions
            if (questions.size == userResponse.responses.size) {
              val sanitizedResponses = sanitizeResponses(questions, userResponse.responses).toList.flatten
              if (questions.size == sanitizedResponses.size) {
                Some((ResponseHeader(session.topic, session.email, session.date, session.session, session.meetup), sanitizedResponses))
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

package controllers

import java.util.Date
import javax.inject.Inject

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.collection.immutable.::
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class FeedbackReportHeader(sessionId: String,
                                topic: String,
                                active: Boolean,
                                session: String,
                                meetUp: Boolean,
                                date: String,
                                rating: String,
                                expired: Boolean)

case class UserFeedbackResponse(email: String, coreMember: Boolean, questionResponse: List[QuestionResponse])

case class FeedbackReport(reportHeader: Option[FeedbackReportHeader], response: List[UserFeedbackResponse])

class FeedbackFormsReportController @Inject()(messagesApi: MessagesApi,
                                              mailerClient: MailerClient,
                                              usersRepository: UsersRepository,
                                              feedbackRepository: FeedbackFormsRepository,
                                              feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                              sessionsRepository: SessionsRepository,
                                              dateTimeUtility: DateTimeUtility,
                                              controllerComponents: KnolxControllerComponents
                                             ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val questionResponseFormat: OFormat[QuestionResponse] = Json.format[QuestionResponse]
  implicit val userFeedbackReportFormat: OFormat[UserFeedbackResponse] = Json.format[UserFeedbackResponse]
  implicit val feedbackReportHeaderFormat: OFormat[FeedbackReportHeader] = Json.format[FeedbackReportHeader]
  implicit val feedbackReportFormat: OFormat[FeedbackReport] = Json.format[FeedbackReport]

  def renderUserFeedbackReports: Action[AnyContent] = userAction.async { implicit request =>
    generateSessionFeedbackReport(Some(request.user.email)).map { reportInfo =>
      Ok(views.html.reports.myreports(reportInfo))
    }
  }

  def renderAllFeedbackReports: Action[AnyContent] = adminAction.async { implicit request =>
    generateSessionFeedbackReport(None).map { reportInfo =>
      Ok(views.html.reports.allreports(reportInfo))
    }
  }

  private def generateSessionFeedbackReport(presenter: Option[String]): Future[List[FeedbackReportHeader]] = {
    presenter.fold {
      val activeSessions = sessionsRepository.activeSessions(None)
      val sessionTillNow = sessionsRepository.userSessionsTillNow(None)
      generateReport(activeSessions, sessionTillNow)
    } { email =>
      val presenterActiveSessions = sessionsRepository.activeSessions(Some(email))
      val presenterSessionTillNow = sessionsRepository.userSessionsTillNow(Some(email))
      generateReport(presenterActiveSessions, presenterSessionTillNow)
    }
  }

  private def generateReport(eventualActiveSessions: Future[List[SessionInfo]],
                             sessionsTillNow: Future[List[SessionInfo]]): Future[List[FeedbackReportHeader]] = {
    eventualActiveSessions flatMap {
      case _ :: _ =>
        sessionsTillNow flatMap {
          case allUserSessionsTillNow@(_ :: _) =>
            eventualActiveSessions map { activeSessions =>
              allUserSessionsTillNow map { session =>
                if (activeSessions.contains(session)) {
                  generateSessionReportHeader(session, active = true)
                } else {
                  generateSessionReportHeader(session, active = false)
                }
              }
            }
          case Nil                             => Future.successful(List.empty)
        }
      case Nil    =>
        sessionsTillNow map {
          case first :: rest => (first +: rest) map (session => generateSessionReportHeader(session, active = false))
          case Nil           => List.empty
        }
    }
  }

  private def generateSessionReportHeader(session: SessionInfo, active: Boolean): FeedbackReportHeader = {
    FeedbackReportHeader(session._id.stringify, session.topic, active = active,
      session.session, session.meetup, new Date(session.date.value).toString, session.rating,
      new Date(session.expirationDate.value).before(new java.util.Date(dateTimeUtility.nowMillis)))
  }

  def fetchUserResponsesBySessionId(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val responses = feedbackFormsResponseRepository.allResponsesBySession(id, Some(request.user.email))

    renderFetchedResponses(responses, id, request.user.superUser).map { report =>
      Ok(views.html.reports.report(report))
    }
  }

  def fetchAllResponsesBySessionId(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    val responses = feedbackFormsResponseRepository.allResponsesBySession(id, None)
    renderFetchedResponses(responses, id, request.user.superUser).map { report =>
      Ok(views.html.reports.report(report))
            }
  }

  private def renderFetchedResponses(responses: Future[List[FeedbackFormsResponse]], id: String, isSuperUser: Boolean): Future[FeedbackReport] = {
    sessionsRepository.getById(id).flatMap(_.fold {
      Logger.error(s" No session found by $id")
      Future.successful(FeedbackReport(None, Nil))
    } { sessionInfo =>
      val header = FeedbackReportHeader(sessionInfo._id.stringify, sessionInfo.topic, active = false,
        sessionInfo.session, sessionInfo.meetup, new Date(sessionInfo.date.value).toString, sessionInfo.rating,
        new Date(sessionInfo.expirationDate.value).before(new java.util.Date(dateTimeUtility.nowMillis)))
      responses.map { sessionResponses =>
        if (sessionResponses.nonEmpty) {
          val questionAndResponses = sessionResponses.map(feedbackResponse =>
            if (isSuperUser) {
              UserFeedbackResponse(feedbackResponse.email, feedbackResponse.coreMember, feedbackResponse.feedbackResponse)
            } else {
              UserFeedbackResponse(" ", feedbackResponse.coreMember, feedbackResponse.feedbackResponse)
            }
          )
          FeedbackReport(Some(header), questionAndResponses)
        } else {
          FeedbackReport(Some(header), Nil)
        }
      }
    })
  }

  def searchAllResponsesBySessionId(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    val responses = feedbackFormsResponseRepository.allResponsesBySession(id, None)
    renderFetchedResponses(responses, id, request.user.superUser).map { report =>
      Ok(Json.toJson(FeedbackReport(report.reportHeader, report.response)).toString())
    }
  }

}

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
                                presenter: String,
                                active: Boolean,
                                session: String,
                                meetUp: Boolean,
                                date: String,
                                rating: String,
                                expired: Boolean)

case class ReportResult(feedbackReportHeaderList: List[FeedbackReportHeader], pageNumber: Int, pages: Int)

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
  implicit val reportResult: OFormat[ReportResult] = Json.format[ReportResult]

  def renderUserFeedbackReports: Action[AnyContent] = userAction.async { implicit request =>
    val sessionTillNow = sessionsRepository.userSessionsTillNow(Some(request.user.email), 1)
    generateReport(sessionTillNow).map { reportInfo =>
      Ok(views.html.reports.myreports(reportInfo))
    }
  }

  def renderAllFeedbackReports: Action[AnyContent] = adminAction.async { implicit request =>
      Future.successful(Ok(views.html.reports.allreports()))
  }

  def manageAllFeedbackReports(pageNumber: Int): Action[AnyContent] = adminAction.async { implicit request =>
    val sessionTillNow = sessionsRepository.userSessionsTillNow(None, pageNumber)
    generateReport(sessionTillNow).flatMap { reportInfo =>
      sessionsRepository
        .activeCount(None)
        .map { count =>
          val pages = Math.ceil(count.toDouble / 8).toInt
          Ok(Json.toJson(ReportResult(reportInfo, pageNumber, pages)))
        }
    }
  }

  private def generateReport(sessionsTillNow: Future[List[SessionInfo]]): Future[List[FeedbackReportHeader]] = {
    sessionsTillNow map { allUserSessionsTillNow =>
        allUserSessionsTillNow map { session =>
          val active = dateTimeUtility.nowMillis < session.expirationDate.value
          generateSessionReportHeader(session, active)
        }
    }
  }

  private def generateSessionReportHeader(session: SessionInfo, active: Boolean): FeedbackReportHeader = {
    FeedbackReportHeader(session._id.stringify, session.topic, session.email, active = active,
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
      val header = FeedbackReportHeader(sessionInfo._id.stringify, sessionInfo.topic, sessionInfo.email, active = false,
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

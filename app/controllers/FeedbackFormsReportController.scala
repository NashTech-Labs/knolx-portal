package controllers

import java.util.Date
import javax.inject.Inject

import models._
import play.api.i18n.{I18nSupport, MessagesApi}
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
                                date: String)

case class FeedbackReport(report: Option[(FeedbackReportHeader, List[List[QuestionResponse]])])

class FeedbackFormsReportController @Inject()(messagesApi: MessagesApi,
                                              mailerClient: MailerClient,
                                              usersRepository: UsersRepository,
                                              feedbackRepository: FeedbackFormsRepository,
                                              feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                              sessionsRepository: SessionsRepository,
                                              dateTimeUtility: DateTimeUtility,
                                              controllerComponents: KnolxControllerComponents
                                             ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

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
    eventualActiveSessions.flatMap {
      case _ :: _ =>
        sessionsTillNow.flatMap {
          case allUserSessionsTillNow@(_ :: _) =>
            eventualActiveSessions.map { activeSessions =>
              allUserSessionsTillNow.map { session =>
                if (activeSessions.contains(session)) {
                  generateSessionReportHeader(session, active = true)
                } else {
                  generateSessionReportHeader(session, active = false)
                }
              }
            }
          case Nil                             => Future.successful(List.empty)
        }
      case Nil    => sessionsTillNow.map {
        case first :: rest => (first +: rest).map(session => generateSessionReportHeader(session, active = false))
        case Nil           => List()
      }
    }
  }

  private def generateSessionReportHeader(session: SessionInfo, active: Boolean): FeedbackReportHeader = {
    FeedbackReportHeader(session._id.stringify, session.topic, active = active,
      session.session, session.meetup, new Date(session.date.value).toString)
  }

  def fetchUserResponsesBySessionId(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val responses = feedbackFormsResponseRepository.allResponsesBySession(id, Some(request.user.email))

    renderFetchedResponses(responses).map { report =>
      Ok(views.html.reports.report(report))
    }
  }

  def fetchAllResponsesBySessionId(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    val responses = feedbackFormsResponseRepository.allResponsesBySession(id, None)

    renderFetchedResponses(responses).map { report =>
      Ok(views.html.reports.report(report))
    }
  }

  private def renderFetchedResponses(responses: Future[List[FeedbackFormsResponse]]): Future[FeedbackReport] = {
    responses.map { sessionResponses =>
      if (sessionResponses.nonEmpty) {
        val response :: _ = sessionResponses
        val header = FeedbackReportHeader(response.sessionId, response.sessionTopic,
          active = false, response.session, response.meetup, new Date(response.sessiondate.value).toString)
        val questionAndResponses = sessionResponses.map(_.feedbackResponse)
        FeedbackReport(Some(header, questionAndResponses))
      } else {
        FeedbackReport(None)
      }
    }
  }

}

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

case class FeedbackReportHeader(sessionId: String, topic: String, active: Boolean, session: String, meetUp: Boolean, date: String)

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

  def renderMyFeedbackReports: Action[AnyContent] = userAction.async { implicit request =>
    feedbackFormsResponseRepository
      .userDistinctSessionsIds(request.user.email).flatMap { myReportIds =>
      generateSessionFeedbackReport(myReportIds, request.user.email).map { reportInfo =>
        Ok(views.html.reports.myreports(reportInfo))
      }
    }
  }

  private def generateSessionFeedbackReport(sessionIds: List[String], presenter: String): Future[List[FeedbackReportHeader]] = {
    val myActiveSessions = sessionsRepository.activeSessions(Some(presenter))
    val presentersSessionTillNow = sessionsRepository.UserSessionsTillNow(presenter)

    myActiveSessions.flatMap {
      case _ :: _ =>
        presentersSessionTillNow.flatMap {
          case allUserSessionsTillNow@(_ :: _) =>
            myActiveSessions.map { activeSessions =>
              allUserSessionsTillNow.map { session =>
                if (activeSessions.contains(session)) {
                  generateSessionReportHeader(session, active = true)
                } else {
                  generateSessionReportHeader(session, active = false)
                }
              }
            }
          case Nil                             => Future.successful(List())
        }
      case Nil    => presentersSessionTillNow.map {
        case first :: rest => (first +: rest).map(session => generateSessionReportHeader(session, active = false))
        case Nil           => List()
      }

    }
  }

  private def generateSessionReportHeader(session: SessionInfo, active: Boolean): FeedbackReportHeader = {
    FeedbackReportHeader(session._id.stringify, session.topic, active = active,
      session.session, session.meetup, new Date(session.date.value).toString)
  }

  def fetchAllResponsesBySessionId(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsResponseRepository.allResponsesBySession(request.user.email, id).map { responses =>
      if (responses.nonEmpty) {
        val response :: _ = responses
        val header = FeedbackReportHeader(response.sessionId, response.sessionTopic,
          active = false, response.session, response.meetup, new Date(response.sessiondate.value).toString)
        val questionAndResponses = responses.map(_.feedbackResponse)
        Ok(views.html.reports.report(FeedbackReport(Some(header, questionAndResponses))))
      } else {
        Ok(views.html.reports.report(FeedbackReport(None)))
      }

    }
  }

}

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

case class FeedbackReport(info: FeedbackReportHeader, questionAndResponse: List[List[QuestionResponse]])

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
        Ok(views.html.reports.myreports(reportInfo.flatten))
      }
    }
  }

  private def generateSessionFeedbackReport(sessionIds: List[String], presenter: String): Future[List[Option[FeedbackReportHeader]]] = {

    val myActiveSessions = sessionsRepository.activeSessions(Some(presenter))

    myActiveSessions.flatMap {
      case first :: rest =>
        val myActiveSessionIds = (first +: rest).map(session => session._id.stringify -> session).toMap
        val myFeedbackReportIds = (myActiveSessionIds.keySet.toList ::: sessionIds).distinct

        Future.sequence(
          myFeedbackReportIds.map { reportId =>
            if (myActiveSessionIds.keySet.contains(reportId)) {
              val session = myActiveSessionIds(reportId)
              Future.successful(Some(FeedbackReportHeader(reportId, session.topic, active = true,
                session.session, session.meetup, new Date(session.date.value).toString)))
            } else {
              generateInactiveSessionReport(reportId, presenter)
            }
          })

      case Nil => Future.sequence(sessionIds.map(sessionId => generateInactiveSessionReport(sessionId, presenter)))
    }
  }

  private def generateInactiveSessionReport(sessionId: String, presenter: String): Future[Option[FeedbackReportHeader]] = {
    feedbackFormsResponseRepository.mySessions(presenter, sessionId).flatMap { response =>
      response.fold {
        val emptyResponse: Option[controllers.FeedbackReportHeader] = None
        Future.successful(emptyResponse)
      } { responseFound =>
        Future.successful(Some(FeedbackReportHeader(sessionId, responseFound.sessionTopic, active = false,
          responseFound.session, responseFound.meetup, new Date(responseFound.sessiondate.value).toString)))
      }
    }
  }

  def fetchAllResponsesBySessionId(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsResponseRepository.allResponsesBySesion(request.user.email, id).map { responses =>

      val response :: _ = responses
      val header = FeedbackReportHeader(response.sessionId, response.sessionTopic,
        active = false, response.session, response.meetup, new Date(response.sessiondate.value).toString)

      val questionAndResponses = responses.map(_.feedbackResponse)

      Ok(views.html.reports.report(FeedbackReport(header, questionAndResponses)))
    }
  }

}

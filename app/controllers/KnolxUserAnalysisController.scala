package controllers

import javax.inject.{Inject, Singleton}

import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class SessionsInformation(topic: String,
                               coreMemberRating: Double,
                               nonCoreMemberRating: Double)


@Singleton
class KnolxUserAnalysisController @Inject()(messagesApi: MessagesApi,
                                            usersRepository: UsersRepository,
                                            sessionsRepository: SessionsRepository,
                                            feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                            feedbackFormsRepository: FeedbackFormsRepository,
                                            controllerComponents: KnolxControllerComponents
                                           ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val sessionInformation: OFormat[SessionsInformation] = Json.format[SessionsInformation]

  def renderUserAnalyticsPage: Action[AnyContent] = adminAction.async { implicit request =>
    Future.successful(Ok(views.html.analysis.useranalysis()))
  }

  def userSessionsResponseComparison(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository.getByEmail(email) flatMap {
      _.fold {
        Future.successful(BadRequest(Json.toJson("User Not Found")))
      } { _ =>
        sessionsRepository.userSession(email).flatMap { sessions =>
          if (sessions.nonEmpty) {
            val sessionsInformation = sessions.map { session =>
              feedbackFormsResponseRepository.getScoresOfMembers(session._id.stringify, isCoreMember = false)
                .map { scores =>
                  val scoresWithoutZero = scores.filterNot(_ == 0)
                  val sessionScore = if (scoresWithoutZero.nonEmpty) scoresWithoutZero.sum / scoresWithoutZero.length else 0.00
                  SessionsInformation(session.topic, session.score, sessionScore)
                }
            }
            val sessionsInformationList = Future.sequence(sessionsInformation)
            sessionsInformationList.flatMap { sessionsList =>
              Future.successful(Ok(Json.toJson(sessionsList)))
            }
          }
          else {
            Future.successful(Ok(Json.toJson(Nil)))
          }
        }
      }
    }
  }

  def getBanCount(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository.getByEmail(email) flatMap {
      _.fold {
        Future.successful(BadRequest(Json.toJson("User Not Found")))
      } { userInfo =>
        val banCount = Map("banCount" -> userInfo.banCount)
        Future.successful(Ok(Json.toJson(banCount)))
      }
    }
  }

  def getUserTotalKnolx(email: String): Action[AnyContent] = adminAction.async {
    usersRepository.getByEmail(email) flatMap {
      _.fold {
        Future.successful(BadRequest(Json.toJson("User Not Found")))
      } { _ =>
        sessionsRepository.userSession(email).map { sessions =>
          if (sessions.nonEmpty) {
            val totalKnolx = Map("totalKnolx" -> sessions.count(!_.meetup))
            Ok(Json.toJson(totalKnolx))
          }
          else {
            val totalKnolx = Map("totalKnolx" -> 0)
            Ok(Json.toJson(totalKnolx))
          }
        }
      }
    }
  }

  def getUserTotalMeetUps(email: String): Action[AnyContent] = adminAction.async {
    usersRepository.getByEmail(email) flatMap {
      _.fold {
        Future.successful(BadRequest(Json.toJson("User Not Found")))
      } { _ =>
        sessionsRepository.userSession(email).map { sessions =>
          if (sessions.nonEmpty) {
            val totalMeetUps = Map("totalMeetUps" -> sessions.count(_.meetup))
            Ok(Json.toJson(totalMeetUps))
          }
          else {
            val totalMeetUps = Map("totalMeetUps" -> 0)
            Ok(Json.toJson(totalMeetUps))
          }
        }
      }
    }
  }

  def getUserDidNotAttendSessionCount(email: String): Action[AnyContent] = adminAction.async {
    usersRepository.getByEmail(email) flatMap {
      _.fold {
        Future.successful(BadRequest(Json.toJson("User Not Found")))
      } { _ =>
        feedbackFormsResponseRepository.userCountDidNotAttendSession(email).map { count =>
          val didNotAttendCount = Map("didNotAttendCount" -> count)
          Ok(Json.toJson(didNotAttendCount))
        }
      }
    }
  }

}

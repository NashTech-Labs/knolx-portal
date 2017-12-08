package controllers


import javax.inject.{Inject, Singleton}

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class SessionsInformation(topic: String,
                               coreMemberResponse: Double,
                               nonCoreMemberResponse: Double)

case class UserAnalysisInformation(email: String,
                                   sessions: List[SessionsInformation],
                                   didNotAttendCount: Int,
                                   banCount: Int,
                                   totalMeetUps: Int,
                                   totalKnolx: Int)

@Singleton
class KnolxUserAnalysisController @Inject()(messagesApi: MessagesApi,
                                            usersRepository: UsersRepository,
                                            sessionsRepository: SessionsRepository,
                                            feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                            feedbackFormsRepository: FeedbackFormsRepository,
                                            controllerComponents: KnolxControllerComponents
                                           ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val sessionInformation: OFormat[SessionsInformation] = Json.format[SessionsInformation]
  implicit val userAnalysisInformation: OFormat[UserAnalysisInformation] = Json.format[UserAnalysisInformation]

  def renderUserAnalyticsPage: Action[AnyContent] = adminAction.async { implicit request =>
    Future.successful(Ok(views.html.analysis.useranalysis()))
  }

  def sendUserList(email: Option[String]): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository.userListSearch(email).map { usersList =>
      Ok(Json.toJson(usersList))
    }
  }

  def userAnalysis(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository.getByEmail(email) flatMap {
      _.fold {
        Future.successful(BadRequest(Json.toJson("User Not Found")))
      } { userInfo =>
        sessionsRepository.userSession(email).flatMap { sessions =>
          if (sessions.nonEmpty) {
            val totalMeetUps = sessions.count(_.meetup)
            val totalKnolx = sessions.count(!_.meetup)

            val sessionsInformation = sessions.map { session =>
              feedbackFormsResponseRepository.getScoresOfMembers(session._id.stringify, false)
                .map { scores =>
                  val scoresWithoutZero = scores.filterNot(_ == 0)
                  val sessionScore = if (scoresWithoutZero.nonEmpty) scoresWithoutZero.sum / scoresWithoutZero.length else 0.00
                  SessionsInformation(session.topic, session.score, sessionScore)
                }
            }

            val sessionsInformationList = Future.sequence(sessionsInformation)
            sessionsInformationList.flatMap { sessionsList =>
              feedbackFormsResponseRepository.userCountDidNotAttendSession(email).map { count =>
                Ok(Json.toJson(UserAnalysisInformation(email, sessionsList, count, userInfo.banCount,
                  totalMeetUps, totalKnolx)))
              }
            }
          }
          else {
            Future.successful(Ok(Json.toJson(UserAnalysisInformation(email, Nil, 0, 0, 0, 0))))
          }
        }
      }
    }
  }

}

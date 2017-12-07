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

case class SessionsInformation(topic: String, rating: Double)

case class UserAnalysisInformation(email: String,
                                   sessions: List[SessionsInformation],
                                   didNotAttendCount: Int,
                                   BanCount: Int,
                                   totalMeetUps: Int,
                                   totalKnolx: Int,
                                   coreMemberResponse: Double,
                                   nonCoreMemberResponse: Double
                                  )

@Singleton
class KnolxUserAnalysisController @Inject()(messagesApi: MessagesApi,
                                            usersRepository: UsersRepository,
                                            sessionsRepository: SessionsRepository,
                                            feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                            feedbackFormsRepository: FeedbackFormsRepository,
                                            categoriesRepository: CategoriesRepository,
                                            dateTimeUtility: DateTimeUtility,
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
            val sessionsInformation = sessions.map(session => SessionsInformation(session.topic, session.score))
            val totalMeetUps = sessions.count(_.meetup)
            val totalKnolx = sessions.count(!_.meetup)

            val nonCoreMemberResponsePerSession = sessions.map { session =>
              feedbackFormsResponseRepository.allResponsesBySession(session._id.stringify, Some(email))
                .map { response =>
                  if (response.nonEmpty) response.filterNot(_.coreMember).map(_.score).sum / response.length else 0D
                }
            }
            val nonCoreMemberResponse = Future.sequence(nonCoreMemberResponsePerSession)

            val coreMemberResponseSum = sessions.map(_.score).sum / sessions.length
            nonCoreMemberResponse.flatMap { sessionsResponse =>

              val nonCoreMemberResponseAverage = sessionsResponse.sum / sessions.length

              feedbackFormsResponseRepository.userCountDidNotAttendSession(email).map { count =>
                Ok(Json.toJson(UserAnalysisInformation(email, sessionsInformation, count, userInfo.banCount,
                  totalMeetUps, totalKnolx, coreMemberResponseSum, nonCoreMemberResponseAverage)))
              }
            }
          } else {
            Future.successful(Ok(Json.toJson(UserAnalysisInformation(email, Nil, 0, 0, 0, 0, 0, 0))))
          }
        }
      }
    }
  }

}

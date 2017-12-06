package controllers


import javax.inject.{Inject, Named, Singleton}

import actors.SessionsScheduler._
import actors.UsersBanScheduler._
import akka.actor.ActorRef
import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


case class SessionsInformation(topic: String, rating: Double)

case class UserAnalysisInformation(email: String,
                                   sessions: List[SessionsInformation],
                                   didNotAttendCount: Int
                                  )

@Singleton
class KnolxUserAnalysisController @Inject()(messagesApi: MessagesApi,
                                            usersRepository: UsersRepository,
                                            sessionsRepository: SessionsRepository,
                                            feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                            feedbackFormsRepository: FeedbackFormsRepository,
                                            categoriesRepository: CategoriesRepository,
                                            dateTimeUtility: DateTimeUtility,
                                            controllerComponents: KnolxControllerComponents,
                                            @Named("SessionsScheduler") sessionsScheduler: ActorRef,
                                            @Named("UsersBanScheduler") usersBanScheduler: ActorRef
                                           ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val sessionInformation: OFormat[SessionsInformation] = Json.format[SessionsInformation]
  implicit val userAnalysisInformation: OFormat[UserAnalysisInformation] = Json.format[UserAnalysisInformation]

  def renderUserAnalyticsPage: Action[AnyContent] = adminAction.async { implicit request =>
    usersRepository.getAllActiveEmails map { usersList =>
      Ok(views.html.analysis.useranalysis(usersList))
    }
  }

  def userAnalysis(email: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository.userSession(email).flatMap { sessions =>
      val sessionsInformation = sessions.map(session =>
        SessionsInformation(session.topic, session.rating.toDouble))
      feedbackFormsResponseRepository.userCountDidNotAttendSession(email).map(count =>
        Ok(Json.toJson(UserAnalysisInformation(email, sessionsInformation, count)))
      )
    }
  }

}

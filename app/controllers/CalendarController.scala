package controllers

import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class CreateSessionInfo(date: Date,
                             session: String,
                             category: String,
                             subCategory: String,
                             topic: String,
                             meetup: Boolean,
                             id: String
                            )

case class CalendarSession(id: String,
                           date: Date,
                           email: String,
                           topic: String,
                           pending: Boolean)

@Singleton
class CalendarController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   approvalSessionsRepository: ApprovalSessionsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   configuration: Configuration,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("EmailManager") emailManager: ActorRef
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val calendarSessionFormat: OFormat[CalendarSession] = Json.format[CalendarSession]

  val createSessionFormByUser = Form(
    mapping(
      "date" -> date("yyyy-MM-dd'T'HH:mm", dateTimeUtility.ISTTimeZone)
        .verifying("Invalid date selected!", date => date.after(new Date(dateTimeUtility.startOfDayMillis))),
      "session" -> nonEmptyText.verifying("Wrong session type specified!",
        session => SessionValues.Sessions.map { case (value, _) => value }.contains(session)),
      "category" -> text.verifying("Please attach a category", !_.isEmpty),
      "subCategory" -> text.verifying("Please attach a sub-category", !_.isEmpty),
      "topic" -> nonEmptyText,
      "meetup" -> boolean,
      "id" -> nonEmptyText
    )(CreateSessionInfo.apply)(CreateSessionInfo.unapply)
  )

  def renderCalendarPage: Action[AnyContent] = action { implicit request =>
    Ok(views.html.calendar.calendar())
  }

  def calendarSessions(startDate: Long, endDate: Long): Action[AnyContent] = action.async { implicit request =>
    sessionsRepository
      .getSessionInMonth(startDate, endDate)
      .flatMap { sessionInfo =>
        val knolxSessions = sessionInfo map { session =>
          CalendarSession(session._id.stringify,
            new Date(session.date.value),
            session.email,
            session.topic,
            pending = false)
        }

        approvalSessionsRepository.getAllSession map { pendingSessions =>
          val pendingSessionForAdmin = pendingSessions map { pendingSession =>
            CalendarSession(pendingSession._id.stringify,
              new Date(pendingSession.date.value),
              pendingSession.email,
              pendingSession.topic,
              pending = true)
          }
          Ok(Json.toJson(knolxSessions ::: pendingSessionForAdmin))
        }
      }
  }

  def renderCreateSessionByUser(sessionId: Option[String]): Action[AnyContent] = userAction.async { implicit request =>
    if (sessionId.isDefined) {
      approvalSessionsRepository.getSession(sessionId.get).map { session =>
        val createSessionInfo = CreateSessionInfo(
          new Date(session.date.value),
          session.session,
          session.category,
          session.subCategory,
          session.topic,
          session.meetup,
          sessionId.get)

        Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser.fill(createSessionInfo)))

      }
    } else {
      Future.successful(Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser)))
    }
  }

  def createSessionByUser: Action[AnyContent] = userAction.async { implicit request =>
    createSessionFormByUser.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for create session $formWithErrors")
        Future.successful(BadRequest(views.html.calendar.createsessionbyuser(createSessionFormByUser)))
      },
      createSessionInfoByUser => {
        val presenterEmail = request.user.email
        val session = models.ApproveSessionInfo(presenterEmail, BSONDateTime(createSessionInfoByUser.date.getTime),
          createSessionInfoByUser.session, createSessionInfoByUser.category,
          createSessionInfoByUser.subCategory,
          createSessionInfoByUser.topic, createSessionInfoByUser.meetup)
        approvalSessionsRepository.insertSessionForApprove(session) flatMap { result =>
          if (result.ok) {
            Logger.info(s"Session By user $presenterEmail successfully created")
            Future.successful(Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!"))
          } else {
            Logger.error(s"Something went wrong when creating a new session for user $presenterEmail")
            Future.successful(InternalServerError("Something went wrong!"))
          }
        }
      })
  }

  def renderPendingSessionPage: Action[AnyContent] = adminAction { implicit request =>
    Ok(views.html.sessions.pendingsession())
  }

}

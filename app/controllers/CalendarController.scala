package controllers

import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import controllers.EmailHelper.isValidEmail
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Logger}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class CreateSessionInfo(email: String,
                             date: Date,
                             category: String,
                             subCategory: String,
                             topic: String,
                             meetup: Boolean
                            )

case class CalendarSession(id: String,
                           date: Date,
                           email: String,
                           topic: String,
                           meetup: Boolean,
                           approved: Boolean,
                           decline: Boolean,
                           pending: Boolean)

case class UpdateApproveSessionInfo(email: String,
                                    date: BSONDateTime,
                                    category: String,
                                    subCategory: String,
                                    topic: String,
                                    meetup: Boolean,
                                    id: String,
                                    approved: Boolean = false,
                                    decline: Boolean = false
                                   )

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
      "email" -> email.verifying("Invalid Email", email => isValidEmail(email)),
      "date" -> date("yyyy-MM-dd'T'HH:mm", dateTimeUtility.ISTTimeZone)
        .verifying("Invalid date selected!", date => date.after(new Date(dateTimeUtility.startOfDayMillis))),
      "category" -> text.verifying("Please attach a category", !_.isEmpty),
      "subCategory" -> text.verifying("Please attach a sub-category", !_.isEmpty),
      "topic" -> nonEmptyText,
      "meetup" -> boolean
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
            session.meetup,
            approved = true,
            decline = false,
            pending = false)
        }

        approvalSessionsRepository.getAllSession map { pendingSessions =>
          val pendingSessionForAdmin = pendingSessions.filterNot(session => session.approved && session.decline ) map { pendingSession =>
            CalendarSession(pendingSession._id.stringify,
              new Date(pendingSession.date.value),
              pendingSession.email,
              pendingSession.topic,
              pendingSession.meetup,
              pendingSession.approved,
              pendingSession.decline,
              pending = true)
          }
          Ok(Json.toJson(knolxSessions ::: pendingSessionForAdmin))
        }
      }
  }

  /*def renderCreateSessionByUser(sessionId: Option[String]): Action[AnyContent] = userAction.async { implicit request =>
    request.body.asFormUrlEncoded.fold {
      Logger.error("Something went wrong while getting data from the request")
      Future.successful(BadRequest("Something went wrong while getting data from the request"))
    } {form =>
      Logger.info("Received request with data")
      form.get("date").fold {
        Logger.info("Something went wrong while getting date from the request")
        Future.successful(BadRequest("Something went wrong while getting date from the request"))
      } { dates =>
        dates.headOption.fold {
          Logger.info("No date found in the request")
          Future.successful(BadRequest("No date found in the request"))
        } { date =>
          if (sessionId.isDefined) {
            approvalSessionsRepository.getSession(sessionId.get).map { session =>
              val createSessionInfo = CreateSessionInfo(
                session.email,
                new Date(session.date.value),
                session.category,
                session.subCategory,
                session.topic,
                session.meetup)

              Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser.fill(createSessionInfo), sessionId, new Date(session.date.value)))
            }
          } else {
            Future.successful(Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser, sessionId, new Date(dateTimeUtility.parseDateStringToIST(date)))))
          }
        }
      }
    }
  }*/

  def renderCreateSessionByUser(sessionId: Option[String], date: String): Action[AnyContent] = userAction.async { implicit request =>
    Logger.info("Date recieved is ----> " + date)
    if (sessionId.isDefined) {
      approvalSessionsRepository.getSession(sessionId.get).map { session =>
        val createSessionInfo = CreateSessionInfo(
          session.email,
          new Date(session.date.value),
          session.category,
          session.subCategory,
          session.topic,
          session.meetup)
        Logger.info("Date converted is ----> " + new Date(date.toLong))
        Logger.info("Date converted is ----> " + new Date(session.date.value))
        Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser.fill(createSessionInfo), sessionId, dateTimeUtility.toLocalDateTime(session.date.value)))
      }
    } else {
      Logger.info("Date converted is ----> " + new Date(date.toLong))
      Future.successful(Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser, sessionId, dateTimeUtility.toLocalDateTime(date.toLong))))
    }
  }

  def createSessionByUser(sessionId: Option[String]): Action[AnyContent] = userAction.async { implicit request =>
    createSessionFormByUser.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for create session $formWithErrors")
        formWithErrors.data.get("date").fold {
          Future.successful(BadRequest("Cannot get date from the request"))
        } { date =>
          Logger.error("33333333333->" + dateTimeUtility.toLocalDateTime(dateTimeUtility.parseDateStringWithTToIST(date)))
          Future.successful(
            BadRequest(views.html.calendar.createsessionbyuser(createSessionFormByUser, sessionId,
              dateTimeUtility.toLocalDateTime(dateTimeUtility.parseDateStringWithTToIST(date))))
          )
        }
      },
      createSessionInfoByUser => {
        val presenterEmail = request.user.email
        val session = UpdateApproveSessionInfo(presenterEmail,
          BSONDateTime(createSessionInfoByUser.date.getTime),
          createSessionInfoByUser.category,
          createSessionInfoByUser.subCategory,
          createSessionInfoByUser.topic,
          createSessionInfoByUser.meetup,
          sessionId.fold("")(identity))
        approvalSessionsRepository.insertSessionForApprove(session) flatMap { result =>
          if (result.ok) {
            Logger.info(s"Session By user $presenterEmail with sessionId ${sessionId.fold("")(identity)} successfully created")
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

  def getPendingSessions: Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.getAllSession map { pendingSessions =>
      val pendingSessionsCount = pendingSessions.length
      Ok(Json.toJson(pendingSessionsCount))
    }
  }

  def getAllSessionForAdmin: Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.getAllSession.map { pendingSessions =>
      val pendingSessionForAdmin = pendingSessions map { pendingSession =>
        CalendarSession(pendingSession._id.stringify,
          new Date(pendingSession.date.value),
          pendingSession.email,
          pendingSession.topic,
          pendingSession.meetup,
          pendingSession.approved,
          pendingSession.decline,
          pending = !pendingSession.approved && !pendingSession.decline )
      }
      Ok(Json.toJson(pendingSessionForAdmin))
    }
  }

}

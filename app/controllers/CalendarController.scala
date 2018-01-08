package controllers

import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import actors.EmailActor
import akka.actor.ActorRef
import controllers.EmailHelper.isValidEmail
import models._
import play.api.data.Form
import play.api.data.Forms.{number, _}
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

case class CreateApproveSessionInfo(email: String,
                                    date: Date,
                                    category: String,
                                    subCategory: String,
                                    topic: String,
                                    meetup: Boolean,
                                    dateString: String
                                   )

case class CalendarSession(id: String,
                           date: Date,
                           email: String,
                           topic: String,
                           meetup: Boolean,
                           dateString: String,
                           approved: Boolean,
                           decline: Boolean,
                           pending: Boolean,
                           freeSlot: Boolean)

case class UpdateApproveSessionInfo(date: BSONDateTime,
                                    sessionId: String = "",
                                    topic: String = "Free slot",
                                    email: String = "",
                                    category: String = "",
                                    subCategory: String = "",
                                    meetup: Boolean = false,
                                    approved: Boolean = false,
                                    decline: Boolean = false,
                                    freeSlot: Boolean = false
                                   )

case class CalendarSessionsWithAuthority(calendarSessions: List[CalendarSession],
                                         isAdmin: Boolean,
                                         loggedIn: Boolean,
                                         email: Option[String])

@Singleton
class CalendarController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   feedbackFormsRepository: FeedbackFormsRepository,
                                   approvalSessionsRepository: ApprovalSessionsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   configuration: Configuration,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("EmailManager") emailManager: ActorRef
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val calendarSessionFormat: OFormat[CalendarSession] = Json.format[CalendarSession]
  implicit val calendarSessionsWithAuthorityFormat: OFormat[CalendarSessionsWithAuthority] = Json.format[CalendarSessionsWithAuthority]
  lazy val fromEmail: String = configuration.getOptional[String]("play.mailer.user").getOrElse("support@knoldus.com")

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
    val isAdmin = SessionHelper.isSuperUser || SessionHelper.isAdmin
    val email = if (SessionHelper.isLoggedIn) None else Some(SessionHelper.email)
    val loggedIn = SessionHelper.isLoggedIn
    sessionsRepository
      .getSessionInMonth(startDate, endDate)
      .flatMap { sessionInfo =>
        val knolxSessions = sessionInfo map { session =>
          CalendarSession(session._id.stringify,
            new Date(session.date.value),
            session.email,
            session.topic,
            session.meetup,
            new Date(session.date.value).toString,
            approved = true,
            decline = false,
            pending = false,
            freeSlot = false)
        }

        approvalSessionsRepository.getAllSession map { pendingSessions =>
          val pendingSessionForAdmin = pendingSessions.filterNot(session => session.approved || session.decline) map { pendingSession =>
            CalendarSession(pendingSession._id.stringify,
              new Date(pendingSession.date.value),
              pendingSession.email,
              pendingSession.topic,
              pendingSession.meetup,
              new Date(pendingSession.date.value).toString,
              pendingSession.approved,
              pendingSession.decline,
              pending = true,
              pendingSession.freeSlot)
          }

          val calendarSessionsWithAuthority = CalendarSessionsWithAuthority(knolxSessions ::: pendingSessionForAdmin, isAdmin, loggedIn, email)
          Ok(Json.toJson(calendarSessionsWithAuthority))
        }
      }
  }

  /*def renderCreateSessionByUser(sessionId: Option[String], date: String): Action[AnyContent] = userAction.async { implicit request =>
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
        Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser.fill(createSessionInfo), sessionId,
          dateTimeUtility.toLocalDateTime(session.date.value)))
      }
    } else {
      Logger.info("Date converted is ----> " + new Date(date.toLong))
      Future.successful(Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser, sessionId,
        dateTimeUtility.toLocalDateTime(date.toLong))))
    }
  }*/

  def renderCreateSessionByUser(sessionId: String, date: String): Action[AnyContent] = userAction.async { implicit request =>
    Logger.info("Date recieved is ----> " + date)
      approvalSessionsRepository.getSession(sessionId).map { session =>
        val createSessionInfo = CreateSessionInfo(
          session.email,
          new Date(session.date.value),
          session.category,
          session.subCategory,
          session.topic,
          session.meetup)
        Logger.info("Date converted is ----> " + new Date(date.toLong))
        Logger.info("Date converted is ----> " + new Date(session.date.value))
        Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser.fill(createSessionInfo), sessionId,
          dateTimeUtility.toLocalDateTime(session.date.value)))
      }
  }

  def createSessionByUser(sessionId: String, date: String): Action[AnyContent] = userAction.async { implicit request =>
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
        val dateString = new Date(dateTimeUtility.parseDateStringWithTToIST(date)).toString
        if (dateString.equals(createSessionInfoByUser.date.toString)) {
          val presenterEmail = request.user.email
          val session = UpdateApproveSessionInfo(
            BSONDateTime(createSessionInfoByUser.date.getTime),
            sessionId,
            createSessionInfoByUser.topic,
            presenterEmail,
            createSessionInfoByUser.category,
            createSessionInfoByUser.subCategory,
            createSessionInfoByUser.meetup)
          approvalSessionsRepository.insertSessionForApprove(session) flatMap { result =>
            if (result.ok) {
              Logger.info(s"Session By user $presenterEmail with sessionId $sessionId successfully created")
              usersRepository.getAllAdminAndSuperUser map {
                adminAndSuperUser =>
                  emailManager ! EmailActor.SendEmail(
                    adminAndSuperUser, fromEmail, s"Session requested: ${createSessionInfoByUser.topic} for ${createSessionInfoByUser.date}",
                    views.html.emails.requestedsessionnotification(session).toString)
                  Logger.error(s"Email has been successfully sent to admin/superUser for session created by $presenterEmail")
              }
              Future.successful(Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!"))
            } else {
              Logger.error(s"Something went wrong when creating a new session for user $presenterEmail")
              Future.successful(InternalServerError("Something went wrong!"))
            }
          }
        } else {
          Future.successful(
            Redirect(routes.CalendarController.renderCreateSessionByUser(sessionId,
              dateTimeUtility.parseDateStringWithTToIST(date).toString)).flashing("message" ->
              "Date submitted was wrong. Please try again.")
          )
        }
      })
  }

  /*def renderPendingSessionPage: Action[AnyContent] = adminAction { implicit request =>
    Ok(views.html.sessions.pendingsession())
  }*/

  def getPendingSessions: Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.getAllSession map { pendingSessions =>
      val pendingSessionsCount = pendingSessions.filterNot(session => session.approved || session.decline).length
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
          new Date(pendingSession.date.value).toString,
          pendingSession.approved,
          pendingSession.decline,
          pending = !pendingSession.approved && !pendingSession.decline,
          freeSlot = false)
      }
      Ok(Json.toJson(pendingSessionForAdmin))
    }
  }

  def updatePendingSessionDate(sessionId: String, date: String): Action[AnyContent] = userAction.async {
    Logger.info("Date received is " + date)
    approvalSessionsRepository.updateDateForPendingSession(sessionId, BSONDateTime(date.toLong)) map { result =>
      if (result.ok) {
        Logger.info("Successfully updated the date")
        Ok("Date for the session was successfully updated")
      } else {
        Logger.info("Something went wrong while updating the date for the session")
        BadRequest("Something went wrong while updating the date for the session")
      }
    }
  }

  def declineSession(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.declineSession(sessionId) map { result =>
      if (result.ok) {
        Logger.info(s"Successfuly declined session $sessionId")
        Ok("Successfully declined the session")
      } else {
        Logger.info(s"Something went wrong while declining session $sessionId")
        BadRequest("Something went wrong while declining session")
      }
    }
  }

  def insertFreeSlot(id: Option[String], date: String): Action[AnyContent] = adminAction.async { implicit request =>
    val formattedDate = BSONDateTime(dateTimeUtility.parseDateStringWithTToIST(date))
    val approveSessionInfo = UpdateApproveSessionInfo(formattedDate, id.fold("")(identity), freeSlot = true)
    approvalSessionsRepository.insertSessionForApprove(approveSessionInfo) map { result =>
      if(result.ok) {
        Ok("Free slot has been entered successfully.")
      } else {
        BadRequest("Something went wrong while entering free slot.")
      }
    }
  }

  def deleteFreeSlot(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.deleteFreeSlot(id) map { result =>
      if(result.ok) {
        Logger.info("Successfully deleted the free slot")
        Ok("Successfully deleted the free slot")
      } else {
        Logger.info("Something went wring while deleting the free slot")
        BadRequest("Something went wring while deleting the free slot")
      }
    }
  }

}

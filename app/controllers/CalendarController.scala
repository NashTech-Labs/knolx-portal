package controllers

import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import actors.EmailActor
import akka.actor.ActorRef
import controllers.EmailHelper.isValidEmail
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, Result}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONDateTime
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
                                    freeSlot: Boolean = false,
                                    recommendationId: String = ""
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
                                   recommendationsRepository: RecommendationsRepository,
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

  def renderCalendarPage(isRecommendation: Boolean = false): Action[AnyContent] = action { implicit request =>
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

        approvalSessionsRepository.getAllSessions map { pendingSessions =>
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

  def renderCreateSessionByUser(sessionId: String,
                                recommendationId: Option[String],
                                isFreeSlot: Boolean): Action[AnyContent] = userAction.async { implicit request =>
    approvalSessionsRepository.getAllFreeSlots flatMap { freeSlots =>
      val freeSlotDates = freeSlots.map { freeSlot =>
        dateTimeUtility.formatDateWithT(new Date(freeSlot.date.value))
      }
      approvalSessionsRepository.getSession(sessionId) flatMap { session =>
        val eventualTopic = recommendationId.fold(Future.successful(session.topic)) { recommendationId =>
          recommendationsRepository.getRecommendationById(recommendationId).map { recommendationInfo =>
            recommendationInfo.fold(session.topic)(_.topic)
          }
        }
        eventualTopic flatMap { topic =>
          val createSessionInfo = CreateSessionInfo(
            session.email,
            new Date(session.date.value),
            session.category,
            session.subCategory,
            topic,
            session.meetup)
          Future.successful(
            Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser.fill(createSessionInfo), sessionId, recommendationId, freeSlotDates, isFreeSlot))
          )
        }
      }
    }
  }

  /*private def createSessionPage(sessionId: String,
                                recommendationTopic: Option[String],
                                isFreeSlot: Boolean): Future[Result] = {
    approvalSessionsRepository.getSession(sessionId) flatMap { session =>
      val createSessionInfo = CreateSessionInfo(
        session.email,
        new Date(session.date.value),
        session.category,
        session.subCategory,
        recommendationTopic.fold(session.topic)(identity),
        session.meetup)
      approvalSessionsRepository.getAllFreeSlots map { freeSlots =>
        val freeSlotDates = freeSlots.map { freeSlot =>
          dateTimeUtility.formatDateWithT(new Date(freeSlot.date.value))
        }
        Ok(views.html.calendar.createsessionbyuser(createSessionFormByUser.fill(createSessionInfo), sessionId, recommendationId, freeSlotDates, isFreeSlot))
      }
    }
  }*/

  def createSessionByUser(sessionId: String, recommendationId: Option[String]): Action[AnyContent] = userAction.async { implicit request =>
    approvalSessionsRepository.getAllFreeSlots flatMap { freeSlots =>
      val freeSlotDates = freeSlots.map { freeSlot =>
        dateTimeUtility.formatDateWithT(new Date(freeSlot.date.value))
      }
      approvalSessionsRepository.getSession(sessionId) flatMap { approveSessionInfo =>
        createSessionFormByUser.bindFromRequest.fold(
          formWithErrors => {
            Logger.error(s"Received a bad request for create session $formWithErrors")
            Future.successful(
              BadRequest(views.html.calendar.createsessionbyuser(formWithErrors, sessionId, recommendationId, freeSlotDates, approveSessionInfo.freeSlot))
            )
          },
          createSessionInfoByUser => {
            val dateString = new Date(approveSessionInfo.date.value).toString
            if (dateString.equals(createSessionInfoByUser.date.toString)) {
              insertSession(request.user.email, createSessionInfoByUser, sessionId, recommendationId)
            } else if (!approveSessionInfo.freeSlot) {
              swapSlots(sessionId, createSessionInfoByUser, approveSessionInfo)
            } else {
              Future.successful(
                Redirect(routes.CalendarController.renderCreateSessionByUser(sessionId, recommendationId, approveSessionInfo.freeSlot)).flashing("message" ->
                  "Date submitted was wrong. Please try again.")
              )
            }
          }
        )
      }
    }
  }

  private def insertSession(presenterEmail: String,
                            createSessionInfoByUser: CreateSessionInfo,
                            sessionId: String,
                            recommendationId: Option[String]) = {
    val session = UpdateApproveSessionInfo(
      BSONDateTime(createSessionInfoByUser.date.getTime),
      sessionId,
      createSessionInfoByUser.topic,
      presenterEmail,
      createSessionInfoByUser.category,
      createSessionInfoByUser.subCategory,
      createSessionInfoByUser.meetup,
      recommendationId = recommendationId.fold("")(identity)
    )
    approvalSessionsRepository.insertSessionForApprove(session) flatMap { status =>
      if (status.ok) {
        Logger.info(s"Session By user $presenterEmail with sessionId $sessionId successfully created")
        usersRepository.getAllAdminAndSuperUser map {
          adminAndSuperUser =>
            emailManager ! EmailActor.SendEmail(
              adminAndSuperUser, fromEmail, s"Session requested: ${createSessionInfoByUser.topic} for ${createSessionInfoByUser.date}",
              views.html.emails.requestedsessionnotification(session).toString)
            Logger.error(s"Email has been successfully sent to admin/superUser for session created by $presenterEmail")

            recommendationId.fold {
              Future.successful(Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!"))
            } { recommendation =>
              recommendationsRepository.bookRecommendation(recommendation) map { result =>
                if (result.ok) {
                  Logger(s"Recommendation has been booked $recommendation")
                  Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!")
                } else {
                  InternalServerError("Something went wrong")
                }

              }
            }
        }
        Future.successful(Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!"))
      } else {
        Logger.error(s"Something went wrong when creating a new session for user $presenterEmail")
        Future.successful(InternalServerError("Something went wrong!"))
      }
    }
  }

  private def swapSlots(sessionId: String,
                        createSessionInfoByUser: CreateSessionInfo,
                        approveSessionInfo: ApproveSessionInfo) = {
    Logger.info("It's not a free slot, let's start swapping")
    approvalSessionsRepository.getFreeSlotByDate(BSONDateTime(createSessionInfoByUser.date.getTime)) flatMap {
      _.fold {
        Future.successful(BadRequest("Free slot on the specified date and time does not exist"))
      } { freeSlot =>
        val newSession = UpdateApproveSessionInfo(
          BSONDateTime(createSessionInfoByUser.date.getTime),
          sessionId,
          createSessionInfoByUser.topic,
          createSessionInfoByUser.email,
          createSessionInfoByUser.category,
          createSessionInfoByUser.subCategory,
          createSessionInfoByUser.meetup)
        approvalSessionsRepository.insertSessionForApprove(newSession) flatMap { result =>
          if (result.ok) {
            val updateFreeSlot = UpdateApproveSessionInfo(approveSessionInfo.date, sessionId = freeSlot._id.stringify, freeSlot = true)
            approvalSessionsRepository.insertSessionForApprove(updateFreeSlot) flatMap { swap =>
              if (swap.ok) {
                Future.successful(Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "The session has been updated successfully."))
              } else {
                Future.successful(InternalServerError("Something went wrong while inserting free slot at session's old date"))
              }
            }
          } else {
            Future.successful(InternalServerError("Something went wrong while updating the session"))
          }
        }
      }
    }
  }

  def getPendingSessions: Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.getAllPendingSession map { pendingSessions =>
      Ok(Json.toJson(pendingSessions.length))
    }
  }

  def getAllSessionForAdmin: Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.getAllBookedSessions.map { pendingSessions =>
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

  def declineSession(sessionId: String,
                     recommendationId: Option[String]): Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.getSession(sessionId).flatMap { approvalSession =>
      approvalSessionsRepository.declineSession(approvalSession._id.stringify) flatMap { session =>
        if (session.ok) {
          Logger.info(s"Successfully declined session $sessionId")
            recommendationsRepository.cancelBookedRecommendation(approvalSession.recommendationId) map { result =>
              if (result.ok) {
                Logger.info(s"Recommendation has been unbooked now ${approvalSession.recommendationId}")
                Redirect(routes.CalendarController.renderCalendarPage())
                  .flashing("message" -> "Recommendation has been unbooked now")
              } else {
                Redirect(routes.SessionsController.renderApproveSessionByAdmin(sessionId, recommendationId))
                  .flashing("message" -> "Something went wrong while declining the session")
              }
            }
          }
        else {
          Logger.info(s"Something went wrong while declining session $sessionId")
          Future.successful(Redirect(routes.SessionsController.renderApproveSessionByAdmin(sessionId, recommendationId))
            .flashing("message" -> "Something went wrong while declining the session"))
        }
      }
    }
  }

  def insertFreeSlot(id: Option[String], date: String): Action[AnyContent] = adminAction.async { implicit request =>
    val formattedDate = BSONDateTime(dateTimeUtility.parseDateStringWithTToIST(date))
    val approveSessionInfo = UpdateApproveSessionInfo(formattedDate, id.fold("")(identity), freeSlot = true)
    approvalSessionsRepository.insertSessionForApprove(approveSessionInfo) map { result =>
      if (result.ok) {
        Ok("Free slot has been entered successfully.")
      } else {
        BadRequest("Something went wrong while entering free slot.")
      }
    }
  }

  def deleteFreeSlot(id: String, recommendationId: Option[String]): Action[AnyContent] = adminAction.async { implicit request =>
    approvalSessionsRepository.deleteFreeSlot(id) map { result =>
      if (result.ok) {
        Logger.info("Successfully deleted the free slot")
        Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Successfully deleted the free slot")
      } else {
        Logger.info("Something went wring while deleting the free slot")
        Redirect(routes.CalendarController.renderCreateSessionByUser(id, recommendationId, isFreeSlot = true))
        Redirect(routes.CalendarController.renderCreateSessionByUser(id, None, isFreeSlot = true))
          .flashing("message" -> "Something went wrong while deleting the free slot")
      }
    }
  }

}

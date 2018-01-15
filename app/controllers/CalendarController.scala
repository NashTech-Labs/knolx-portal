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
import play.api.mvc.{Action, AnyContent}
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
                             meetup: Boolean)

case class CreateApproveSessionInfo(email: String,
                                    date: Date,
                                    category: String,
                                    subCategory: String,
                                    topic: String,
                                    meetup: Boolean,
                                    dateString: String)

case class CalendarSession(id: String,
                           date: Date,
                           email: String,
                           topic: String,
                           meetup: Boolean,
                           dateString: String,
                           approved: Boolean,
                           decline: Boolean,
                           pending: Boolean,
                           freeSlot: Boolean,
                           contentAvailable: Boolean = false)

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
                                    recommendationId: String = "")

case class CalendarSessionsWithAuthority(calendarSessions: List[CalendarSession],
                                         isAdmin: Boolean,
                                         loggedIn: Boolean,
                                         email: Option[String])

case class CalendarSessionsSearchResult(calendarSessions: List[CalendarSession],
                                        pages: Int,
                                        page: Int,
                                        keyword: String,
                                        totalSessions: Int)

case class FreeSlot(id: String, date: String)

@Singleton
class CalendarController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   sessionRequestRepository: SessionRequestRepository,
                                   recommendationsRepository: RecommendationsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   configuration: Configuration,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("EmailManager") emailManager: ActorRef
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val calendarSessionFormat: OFormat[CalendarSession] = Json.format[CalendarSession]
  implicit val calendarSessionsWithAuthorityFormat: OFormat[CalendarSessionsWithAuthority] = Json.format[CalendarSessionsWithAuthority]
  implicit val calendarSessionsSearchResultFormat: OFormat[CalendarSessionsSearchResult] = Json.format[CalendarSessionsSearchResult]

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

  val sessionSearchForm = Form(
    mapping(
      "email" -> optional(nonEmptyText),
      "page" -> number.verifying("Invalid Page Number", _ >= 1),
      "pageSize" -> number.verifying("Invalid Page size", _ >= 10)
    )(SessionEmailInformation.apply)(SessionEmailInformation.unapply)
  )

  def renderCalendarPage(isRecommendation: Boolean = false): Action[AnyContent] = action { implicit request =>
    Ok(views.html.calendar.calendar())
  }

  def calendarSessions(startDate: Long, endDate: Long): Action[AnyContent] = action.async { implicit request =>
    val isAdmin = SessionHelper.isSuperUser || SessionHelper.isAdmin
    val loggedIn = !SessionHelper.isLoggedIn
    val email = if (loggedIn) Some(SessionHelper.email) else None
    sessionsRepository
      .getSessionInMonth(startDate, endDate)
      .flatMap { sessionInfo =>
        val knolxSessions = sessionInfo map { session =>
          val contentAvailable = session.youtubeURL.isDefined || session.slideShareURL.isDefined
          CalendarSession(session._id.stringify,
            new Date(session.date.value),
            session.email,
            session.topic,
            session.meetup,
            new Date(session.date.value).toString,
            approved = true,
            decline = false,
            pending = false,
            freeSlot = false,
            contentAvailable = contentAvailable)
        }

        sessionRequestRepository.getSessionsInMonth(startDate, endDate) map { pendingSessions =>
          val pendingSessionForAdmin =
            pendingSessions
              .filterNot(session => session.approved || session.decline)
              .map { pendingSession =>
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

          val calendarSessionsWithAuthority =
            CalendarSessionsWithAuthority(knolxSessions ::: pendingSessionForAdmin, isAdmin, loggedIn, email)
          Ok(Json.toJson(calendarSessionsWithAuthority))
        }
      }
  }

  def renderCreateSessionByUser(sessionId: String,
                                recommendationId: Option[String],
                                isFreeSlot: Boolean): Action[AnyContent] = userAction.async { implicit request =>
    sessionRequestRepository
      .getAllFreeSlots
      .flatMap { freeSlots =>
        val freeSlotsInfo = freeSlots.map(freeSlot =>
          FreeSlot(freeSlot._id.stringify, dateTimeUtility.formatDateWithT(new Date(freeSlot.date.value))))

        sessionRequestRepository.getSession(sessionId) flatMap { maybeApproveSessionInfo =>
          maybeApproveSessionInfo.fold {
            Future.successful(Redirect(routes.CalendarController.renderCalendarPage())
              .flashing("message" -> "The selected session does not exist"))
          } { session =>
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

              if (session.freeSlot == isFreeSlot) {
                Future.successful(Ok(views.html.calendar.createsessionbyuser(
                  createSessionFormByUser.fill(createSessionInfo), sessionId, recommendationId, freeSlotsInfo, isFreeSlot)
                ))
              } else {
                Future.successful(
                  Redirect(routes.CalendarController.renderCalendarPage())
                    .flashing("error" -> "The selected session does not exist"))
              }
            }
          }
        }
      }
  }

  def createSessionByUser(sessionId: String,
                          recommendationId: Option[String]): Action[AnyContent] = userAction.async { implicit request =>
    sessionRequestRepository
      .getAllFreeSlots
      .flatMap { freeSlots =>
        val freeSlotsInfo = freeSlots.map(freeSlot =>
          FreeSlot(freeSlot._id.stringify, dateTimeUtility.formatDateWithT(new Date(freeSlot.date.value))))
        sessionRequestRepository
          .getSession(sessionId)
          .flatMap { maybeApproveSessionInfo =>
            maybeApproveSessionInfo.fold {
              Future.successful(Redirect(routes.CalendarController.renderCalendarPage())
                .flashing("error" -> "The selected session does not exist"))
            } { approveSessionInfo =>
              createSessionFormByUser.bindFromRequest.fold(
                formWithErrors => {
                  Logger.error(s"Received a bad request while creating the session $formWithErrors")
                  Future.successful(
                    BadRequest(views.html.calendar.createsessionbyuser(
                      formWithErrors, sessionId, recommendationId, freeSlotsInfo, approveSessionInfo.freeSlot)
                    )
                  )
                },
                createSessionInfoByUser => {
                  val freeSlotId = request.body.asFormUrlEncoded.fold("") { form =>
                    form.get("freeSlotId").fold("")(_.headOption.fold("")(identity))
                  }
                  val dateString = new Date(approveSessionInfo.date.value).toString

                  if (freeSlotId.isEmpty) {
                    Future.successful(
                      Redirect(routes.CalendarController.renderCreateSessionByUser(sessionId, recommendationId, approveSessionInfo.freeSlot))
                        .flashing("message" -> "Free slot doesn't exist")
                    )
                  } else if (dateString.equals(createSessionInfoByUser.date.toString)) {
                    insertSession(request.user.email, createSessionInfoByUser, sessionId, recommendationId)
                  } else if (!approveSessionInfo.freeSlot) {
                    swapSlots(sessionId, createSessionInfoByUser, approveSessionInfo, freeSlotId)
                  } else {
                    Future.successful(
                      Redirect(routes.CalendarController.renderCreateSessionByUser(sessionId, recommendationId, approveSessionInfo.freeSlot))
                        .flashing("message" -> "Date submitted was wrong. Please try again.")
                    )
                  }
                }
              )
            }
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
    sessionRequestRepository
      .insertSessionForApprove(session)
      .flatMap { status =>
        if (status.ok) {
          Logger.info(s"Session By user $presenterEmail with sessionId $sessionId successfully created")

          usersRepository
            .getAllAdminAndSuperUser
            .map { adminAndSuperUser =>
              emailManager ! EmailActor.SendEmail(
                adminAndSuperUser, fromEmail,
                s"Session requested: ${createSessionInfoByUser.topic} for ${createSessionInfoByUser.date}",
                views.html.emails.requestedsessionnotification(session).toString)
              Logger.info(s"Email has been successfully sent to admin/superUser for session created by $presenterEmail")
            }

          recommendationId.fold {
            Future.successful(Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!"))
          } { recommendation =>
            recommendationsRepository.bookRecommendation(recommendation) map { result =>
              if (result.ok) {
                Logger(s"Recommendation has been booked $recommendation")
                Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!")
              } else {
                InternalServerError("Something went wrong while inserting session for respective recommendation")
              }
            }
          }
          Future.successful(Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Session successfully created!"))
        }
        else {
          Logger.error(s"Something went wrong when creating a new session for user $presenterEmail")
          Future.successful(InternalServerError("Something went wrong while inserting session"))
        }
      }
  }


  private def swapSlots(sessionId: String,
                        createSessionInfoByUser: CreateSessionInfo,
                        approveSessionInfo: SessionRequestInfo,
                        freeSlotId: String) = {
    sessionRequestRepository
      .getSession(freeSlotId)
      .flatMap {
        _.fold {
          Future.successful(BadRequest("Free slot on the specified date and time does not exist"))
        } { freeSlot =>
          val newDate = BSONDateTime(createSessionInfoByUser.date.getTime)

          sessionRequestRepository
            .updateDateForPendingSession(sessionId, newDate)
            .flatMap { result =>
              if (result.ok) {
                val updateFreeSlot = UpdateApproveSessionInfo(
                  approveSessionInfo.date, sessionId = freeSlot._id.stringify, freeSlot = true)

                sessionRequestRepository
                  .insertSessionForApprove(updateFreeSlot)
                  .flatMap { swap =>
                    if (swap.ok) {
                      Future.successful(Redirect(routes.CalendarController.renderCalendarPage())
                        .flashing("message" -> "The session has been updated successfully."))
                    } else {
                      Future.successful(
                        InternalServerError("Something went wrong while inserting a free slot on session's previous date")
                      )
                    }
                  }
              } else {
                Future.successful(InternalServerError("Something went wrong while updating the session"))
              }
            }
        }
      }
  }

  def pendingSessions: Action[AnyContent] = adminAction.async { implicit request =>
    sessionRequestRepository.getAllPendingSession map { pendingSessions =>
      Ok(Json.toJson(pendingSessions.length))
    }
  }

  def allSessionForAdmin: Action[AnyContent] = adminAction.async { implicit request =>
    sessionSearchForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received form with errors while getting all sessions for admin ==> $formWithErrors")
        Future.successful(BadRequest("Oops! Invalid value encountered!"))
      },
      sessionInformation => {
        sessionRequestRepository
          .paginate(sessionInformation.page, sessionInformation.email, sessionInformation.pageSize)
          .flatMap { pendingSessions =>
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

            sessionRequestRepository
              .activeCount(sessionInformation.email)
              .map { count =>
                val pages = Math.ceil(count.toDouble / sessionInformation.pageSize).toInt

                Ok(Json.toJson(CalendarSessionsSearchResult(pendingSessionForAdmin,
                  pages, sessionInformation.page, sessionInformation.email.getOrElse(""),
                  count)))
              }
          }
      })
  }

  def declineSession(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionRequestRepository
      .getSession(sessionId)
      .flatMap { maybeApproveSessionInfo =>
        maybeApproveSessionInfo.fold {
          Future.successful(Redirect(routes.CalendarController.renderCalendarPage())
            .flashing("message" -> "The selected session does not exist"))
        } { approvalSession =>
          sessionRequestRepository
            .declineSession(approvalSession._id.stringify)
            .flatMap { session =>
              if (session.ok) {
                Logger.info(s"Successfully declined session $sessionId" + "--->" + approvalSession.recommendationId)
                approvalSession.recommendationId.isEmpty match {
                  case false => recommendationsRepository.cancelBookedRecommendation(approvalSession.recommendationId) map { result =>
                    if (result.ok) {
                      Logger.info(s"Recommendation has been unbooked now ${approvalSession.recommendationId}")
                      Redirect(routes.CalendarController.renderCalendarPage())
                        .flashing("message" -> "Recommendation has been unbooked now")
                    } else {
                      Redirect(routes.SessionsController.renderScheduleSessionByAdmin(sessionId, Some(approvalSession.recommendationId)))
                        .flashing("message" -> "Something went wrong while declining the session")
                    }
                  }
                  case true  => Future.successful(Redirect(routes.CalendarController.renderCalendarPage())
                    .flashing("message" -> "Sessions has been declined"))
                }
              } else {
                Logger.info(s"Something went wrong while declining session $sessionId")
                Future.successful(Redirect(routes.SessionsController.renderScheduleSessionByAdmin(sessionId, Some(approvalSession.recommendationId)))
                  .flashing("message" -> "Something went wrong while declining the session"))
              }
            }
        }
      }
  }

  def insertFreeSlot(date: String): Action[AnyContent] = adminAction.async { implicit request =>
    val formattedDate = BSONDateTime(dateTimeUtility.parseDateStringWithTToIST(date))
    val approveSessionInfo = UpdateApproveSessionInfo(formattedDate, freeSlot = true)

    sessionRequestRepository
      .insertSessionForApprove(approveSessionInfo)
      .map { result =>
        if (result.ok) {
          Ok("Free slot has been entered successfully.")
        } else {
          BadRequest("Something went wrong while inserting free slot.")
        }
      }
  }

  def deleteFreeSlot(id: String, recommendationId: Option[String]): Action[AnyContent] = adminAction.async { implicit request =>
    sessionRequestRepository
      .deleteFreeSlot(id)
      .map { result =>
        if (result.ok) {
          Logger.info("Successfully deleted the free slot")
          Redirect(routes.CalendarController.renderCalendarPage()).flashing("message" -> "Successfully deleted the free slot")
        } else {
          Logger.error("Something went wring while deleting the free slot")
          Redirect(routes.CalendarController.renderCreateSessionByUser(id, recommendationId, isFreeSlot = true))
            .flashing("message" -> "Something went wrong while deleting the free slot")
        }
      }
  }

}

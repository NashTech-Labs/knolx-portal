package controllers

import java.time._
import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import actors.SessionsScheduler._
import actors.UsersBanScheduler.{RefreshSessionsBanSchedulers, ScheduledBanSessionsNotRefreshed, ScheduledBanSessionsRefreshed, SessionBanSchedulerResponse}
import akka.actor.ActorRef
import akka.pattern.ask
import models.{FeedbackFormsRepository, SessionsRepository, UpdateSessionInfo, UsersRepository}
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case class CreateSessionInformation(email: String,
                                    date: Date,
                                    session: String,
                                    feedbackFormId: String,
                                    topic: String,
                                    feedbackExpirationDays: Int,
                                    meetup: Boolean)

case class UpdateSessionInformation(id: String,
                                    date: Date,
                                    session: String,
                                    feedbackFormId: String,
                                    topic: String,
                                    feedbackExpirationDays: Int,
                                    meetup: Boolean = false)

case class KnolxSession(id: String,
                        userId: String,
                        date: Date,
                        session: String,
                        topic: String,
                        email: String,
                        meetup: Boolean,
                        cancelled: Boolean,
                        rating: String,
                        feedbackFormScheduled: Boolean = false,
                        dateString: String = "",
                        completed: Boolean = false)

case class SessionEmailInformation(email: Option[String], page: Int)

case class SessionSearchResult(sessions: List[KnolxSession],
                               pages: Int,
                               page: Int,
                               keyword: String)

object SessionValues {
  val Sessions = 1 to 5 map (number => (s"session $number", s"Session $number"))
}

@Singleton
class SessionsController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   feedbackFormsRepository: FeedbackFormsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("SessionsScheduler") sessionsScheduler: ActorRef,
                                   @Named("UsersBanScheduler") usersBanScheduler: ActorRef
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val knolxSessionInfoFormat: OFormat[KnolxSession] = Json.format[KnolxSession]
  implicit val sessionSearchResultInfoFormat: OFormat[SessionSearchResult] = Json.format[SessionSearchResult]

  val sessionSearchForm = Form(
    mapping(
      "email" -> optional(nonEmptyText),
      "page" -> number.verifying("Invalid feedback form expiration days selected", _ >= 1)
    )(SessionEmailInformation.apply)(SessionEmailInformation.unapply)
  )

  val createSessionForm = Form(
    mapping(
      "email" -> email,
      "date" -> date("yyyy-MM-dd'T'HH:mm", dateTimeUtility.ISTTimeZone)
        .verifying("Invalid date selected!", date => date.after(new Date(dateTimeUtility.startOfDayMillis))),
      "session" -> nonEmptyText.verifying("Wrong session type specified!",
        session => SessionValues.Sessions.map { case (value, _) => value }.contains(session)),
      "feedbackFormId" -> text.verifying("Please attach a feedback form template", !_.isEmpty),
      "topic" -> nonEmptyText,
      "feedbackExpirationDays" -> number.verifying("Invalid feedback form expiration days selected", number => number >= 0 && number <= 31),
      "meetup" -> boolean
    )(CreateSessionInformation.apply)(CreateSessionInformation.unapply)
  )
  val updateSessionForm = Form(
    mapping(
      "sessionId" -> nonEmptyText,
      "date" -> date("yyyy-MM-dd'T'HH:mm", dateTimeUtility.ISTTimeZone),
      "session" -> nonEmptyText.verifying("Wrong session type specified!",
        session => SessionValues.Sessions.map { case (value, _) => value }.contains(session)),
      "feedbackFormId" -> text.verifying("Please attach a feedback form template", !_.isEmpty),
      "topic" -> nonEmptyText,
      "feedbackExpirationDays" -> number.verifying("Invalid feedback form expiration days selected, " +
        "must be in range 1 to 31", number => number >= 0 && number <= 31),
      "meetup" -> boolean
    )(UpdateSessionInformation.apply)(UpdateSessionInformation.unapply)
  )

  def sessions(pageNumber: Int = 1, keyword: Option[String] = None): Action[AnyContent] = action.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber, keyword)
      .flatMap { sessionInfo =>
        val knolxSessions = sessionInfo map (session =>
          KnolxSession(session._id.stringify,
            session.userId,
            new Date(session.date.value),
            session.session,
            session.topic,
            session.email,
            session.meetup,
            session.cancelled,
            session.rating))

        sessionsRepository
          .activeCount(keyword)
          .map { count =>
            val pages = Math.ceil(count / 10D).toInt
            Ok(views.html.sessions.sessions(knolxSessions, pages, pageNumber))
          }
      }
  }

  def searchSessions: Action[AnyContent] = action.async { implicit request =>
    sessionSearchForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage ==> $formWithErrors")
        Future.successful(BadRequest(" OOps! Invalid value encountered !"))
      },
      sessionInformation => {
        sessionsRepository
          .paginate(sessionInformation.page, sessionInformation.email)
          .flatMap { sessionInfo =>
            val knolxSessions = sessionInfo map (session =>
              KnolxSession(session._id.stringify,
                session.userId,
                new Date(session.date.value),
                session.session,
                session.topic,
                session.email,
                session.meetup,
                session.cancelled,
                session.rating,
                dateString = new Date(session.date.value).toString))

            sessionsRepository
              .activeCount(sessionInformation.email)
              .map { count =>
                val pages = Math.ceil(count / 10D).toInt

                Ok(Json.toJson(SessionSearchResult(knolxSessions, pages, sessionInformation.page, sessionInformation.email.getOrElse(""))).toString)
              }
          }
      })
  }

  def manageSessions(pageNumber: Int = 1, keyword: Option[String] = None): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber, keyword)
      .flatMap { sessionsInfo =>
        val knolxSessions =
          sessionsInfo map (sessionInfo =>
            KnolxSession(sessionInfo._id.stringify,
              sessionInfo.userId,
              new Date(sessionInfo.date.value),
              sessionInfo.session,
              sessionInfo.topic,
              sessionInfo.email,
              sessionInfo.meetup,
              sessionInfo.cancelled,
              sessionInfo.rating))

        val eventualScheduledFeedbackForms =
          (sessionsScheduler ? GetScheduledSessions) (5.seconds).mapTo[ScheduledSessions]

        val eventualKnolxSessions = eventualScheduledFeedbackForms map { scheduledFeedbackForms =>
          knolxSessions map { session =>
            val scheduled = scheduledFeedbackForms.sessionIds.contains(session.id)

            session.copy(feedbackFormScheduled = scheduled)
          }
        }

        eventualKnolxSessions flatMap { sessions =>
          sessionsRepository
            .activeCount(keyword)
            .map { count =>
              val pages = Math.ceil(count / 10D).toInt
              Ok(views.html.sessions.managesessions(sessions, pages, pageNumber))
            }
        }
      }
  }

  def searchManageSession: Action[AnyContent] = adminAction.async { implicit request =>
    sessionSearchForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage ==> $formWithErrors")
        Future.successful(BadRequest(" OOps! Invalid value encountered !"))
      },
      sessionInformation => {
        sessionsRepository
          .paginate(sessionInformation.page, sessionInformation.email)
          .flatMap { sessionInfo =>
            val knolxSessions = sessionInfo map (sessionInfo =>
              KnolxSession(sessionInfo._id.stringify,
                sessionInfo.userId,
                new Date(sessionInfo.date.value),
                sessionInfo.session,
                sessionInfo.topic,
                sessionInfo.email,
                sessionInfo.meetup,
                sessionInfo.cancelled,
                sessionInfo.rating,
                dateString = new Date(sessionInfo.date.value).toString,
                completed = new Date(sessionInfo.date.value).before(new java.util.Date(System.currentTimeMillis))
              ))

            val eventualScheduledFeedbackForms =
              (sessionsScheduler ? GetScheduledSessions) (5.seconds).mapTo[ScheduledSessions]

            val eventualKnolxSessions = eventualScheduledFeedbackForms map { scheduledFeedbackForms =>
              knolxSessions map { session =>
                val scheduled = scheduledFeedbackForms.sessionIds.contains(session.id)
                session.copy(feedbackFormScheduled = scheduled)
              }
            }
            eventualKnolxSessions flatMap { sessions =>
              sessionsRepository
                .activeCount(sessionInformation.email)
                .map { count =>
                  val pages = Math.ceil(count / 10D).toInt

                  Ok(Json.toJson(SessionSearchResult(sessions, pages, sessionInformation.page, sessionInformation.email.getOrElse(""))).toString)
                }
            }
          }
      })
  }

  def create: Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .map { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))

        Ok(views.html.sessions.createsession(createSessionForm, formIds))
      }
  }

  def createSession: Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .flatMap { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))

        createSessionForm.bindFromRequest.fold(
          formWithErrors => {
            Logger.error(s"Received a bad request for create session $formWithErrors")
            Future.successful(BadRequest(views.html.sessions.createsession(formWithErrors, formIds)))
          },
          createSessionInfo => {
            usersRepository
              .getByEmail(createSessionInfo.email.toLowerCase)
              .flatMap(_.fold {
                Future.successful(
                  BadRequest(views.html.sessions.createsession(createSessionForm.fill(createSessionInfo).withGlobalError("Email not valid!"), formIds)))
              } { userJson =>
                val expirationDateMillis = sessionExpirationMillis(createSessionInfo.date, createSessionInfo.feedbackExpirationDays)
                val session = models.SessionInfo(userJson._id.stringify, createSessionInfo.email.toLowerCase,
                  BSONDateTime(createSessionInfo.date.getTime), createSessionInfo.session, createSessionInfo.feedbackFormId,
                  createSessionInfo.topic, createSessionInfo.feedbackExpirationDays, createSessionInfo.meetup, rating = "",
                  cancelled = false, active = true, BSONDateTime(expirationDateMillis))

                sessionsRepository.insert(session) flatMap { result =>
                  if (result.ok) {
                    Logger.info(s"Session for user ${createSessionInfo.email} successfully created")

                    (sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse] map {
                      case ScheduledSessionsRefreshed    =>
                        Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Session successfully created!")
                      case ScheduledSessionsNotRefreshed =>
                        Logger.error(s"Cannot refresh feedback form actors while creating session ${createSessionInfo.topic}")
                        InternalServerError("Something went wrong!")
                      case msg                           =>
                        Logger.error(s"Something went wrong when refreshing feedback form actors $msg while creating session ${createSessionInfo.topic}")
                        InternalServerError("Something went wrong!")
                    }
                  } else {
                    Logger.error(s"Something went wrong when creating a new Knolx session for user ${createSessionInfo.email}")
                    Future.successful(InternalServerError("Something went wrong!"))
                  }
                }
              })
          })
      }
  }

  private def sessionExpirationMillis(date: Date, customDays: Int): Long =
    if (customDays > 0) {
      customSessionExpirationMillis(date, customDays)
    } else {
      defaultSessionExpirationMillis(date)
    }

  private def defaultSessionExpirationMillis(date: Date): Long = {
    val scheduledDate = dateTimeUtility.toLocalDateTimeEndOfDay(date)

    val expirationDate = scheduledDate.getDayOfWeek match {
      case DayOfWeek.FRIDAY   => scheduledDate.plusDays(3)
      case DayOfWeek.SATURDAY => scheduledDate.plusDays(2)
      case _: DayOfWeek       => scheduledDate.plusDays(1)
    }

    dateTimeUtility.toMillis(expirationDate)
  }

  private def customSessionExpirationMillis(date: Date, days: Int): Long = {
    val scheduledDate = dateTimeUtility.toLocalDateTimeEndOfDay(date)
    val expirationDate = scheduledDate.plusDays(days)

    dateTimeUtility.toMillis(expirationDate)
  }

  def deleteSession(id: String, pageNumber: Int): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .delete(id)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete Knolx session with id $id")
        Future.successful(InternalServerError("Something went wrong!"))
      } { _ =>
        Logger.info(s"Knolx session $id successfully deleted")

        (sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse] map {
          case ScheduledSessionsRefreshed    =>
            Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Session successfully Deleted!")
          case ScheduledSessionsNotRefreshed =>
            Logger.error(s"Cannot refresh feedback form actors while deleting session $id")
            InternalServerError("Something went wrong!")
          case msg                           =>
            Logger.error(s"Something went wrong when refreshing feedback form actors $msg while deleting session $id")
            InternalServerError("Something went wrong!")
        }
      })
  }

  def update(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .getById(id)
      .flatMap {
        case Some(sessionInformation) =>
          feedbackFormsRepository
            .getAll
            .map { feedbackForms =>
              val formIds = feedbackForms.map(form => (form._id.stringify, form.name))
              val filledForm = updateSessionForm.fill(UpdateSessionInformation(sessionInformation._id.stringify,
                new Date(sessionInformation.date.value), sessionInformation.session,
                sessionInformation.feedbackFormId, sessionInformation.topic, sessionInformation.feedbackExpirationDays, sessionInformation.meetup))
              Ok(views.html.sessions.updatesession(filledForm, formIds))
            }

        case None => Future.successful(Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Something went wrong!"))
      }
  }

  def updateSession(): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .flatMap { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))
        updateSessionForm.bindFromRequest.fold(
          formWithErrors => {
            Logger.error(s"Received a bad request for getByEmail session $formWithErrors")
            Future.successful(BadRequest(views.html.sessions.updatesession(formWithErrors, formIds)))
          },
          updateSessionInfo => {
            val expirationMillis = sessionExpirationMillis(updateSessionInfo.date, updateSessionInfo.feedbackExpirationDays)
            val updatedSession = UpdateSessionInfo(updateSessionInfo, BSONDateTime(expirationMillis))
            sessionsRepository
              .update(updatedSession)
              .flatMap { result =>
                if (result.ok) {
                  Logger.info(s"Successfully updated session ${updateSessionInfo.id}")
                  (usersBanScheduler ? RefreshSessionsBanSchedulers) (5.seconds).mapTo[SessionBanSchedulerResponse] map {
                    case ScheduledBanSessionsRefreshed    =>
                      Logger.error(s"Refreshed Ban schedulers while updating session ${updateSessionInfo.id}")
                    case ScheduledBanSessionsNotRefreshed =>
                      Logger.error(s"Cannot refresh ban schedulers while updating session ${updateSessionInfo.id}")
                    case msg                              =>
                      Logger.error(s"Something went wrong when refreshing ban schedulers form actors $msg while updating session ${updateSessionInfo.id}")
                  }
                  (sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse] map {
                    case ScheduledSessionsRefreshed    =>
                      Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Session successfully updated")
                    case ScheduledSessionsNotRefreshed =>
                      Logger.error(s"Cannot refresh feedback form actors while updating session ${updateSessionInfo.id}")
                      InternalServerError("Something went wrong!")
                    case msg                           =>
                      Logger.error(s"Something went wrong when refreshing feedback form actors $msg while updating session ${updateSessionInfo.id}")
                      InternalServerError("Something went wrong!")
                  }
                } else {
                  Logger.error(s"Something went wrong when updating a new Knolx session for user  ${updateSessionInfo.id}")
                  Future.successful(InternalServerError("Something went wrong!"))
                }
              }
          })
      }
  }

  def cancelScheduledSession(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    (sessionsScheduler ? CancelScheduledSession(sessionId)) (5.seconds).mapTo[Boolean] map {
      case true  =>
        Redirect(routes.SessionsController.manageSessions(1, None))
          .flashing("message" -> "Scheduled feedback form successfully cancelled!")
      case false =>
        Redirect(routes.SessionsController.manageSessions(1, None))
          .flashing("message" -> "Either feedback form was already sent or Something went wrong while removing scheduled feedback form!")
    }
  }

  def scheduleSession(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsScheduler ! ScheduleSession(sessionId)
    Future.successful(Redirect(routes.SessionsController.manageSessions(1, None))
      .flashing("message" -> "Feedback form schedule initiated"))
  }

}

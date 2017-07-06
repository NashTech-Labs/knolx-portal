package controllers

import java.util.Date
import javax.inject.{Named, Inject, Singleton}

import akka.actor.ActorRef
import models.{FeedbackFormsRepository, SessionsRepository, UsersRepository}
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Controller}
import reactivemongo.bson.BSONDateTime
import schedulers.SessionsScheduler._
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask

case class CreateSessionInformation(email: String,
                                    date: Date,
                                    session: String,
                                    feedbackFormId: String,
                                    topic: String,
                                    meetup: Boolean)

case class UpdateSessionInformation(_id: String,
                                    date: Date,
                                    session: String,
                                    feedbackFormId: String,
                                    topic: String,
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
                        feedbackFormScheduled: Boolean = false)

object SessionValues {
  val Sessions = Seq("session 1" -> "Session 1", "session 2" -> "Session 2")
}

@Singleton
class SessionsController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   feedbackFormsRepository: FeedbackFormsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("SessionsScheduler") sessionsScheduler: ActorRef
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  val usersRepo: UsersRepository = usersRepository

  val createSessionForm = Form(
    mapping(
      "email" -> email,
      "date" -> date("yyyy-MM-dd'T'HH:mm").verifying("Invalid date selected!", date => date.after(new Date(dateTimeUtility.startOfDayMillis))),
      "session" -> nonEmptyText.verifying("Wrong session type specified!", session => session == "session 1" || session == "session 2"),
      "feedbackFormId" -> nonEmptyText,
      "topic" -> nonEmptyText,
      "meetup" -> boolean
    )(CreateSessionInformation.apply)(CreateSessionInformation.unapply)
  )
  val updateSessionForm = Form(
    mapping(
      "sessionId" -> nonEmptyText,
      "date" -> date("yyyy-MM-dd'T'HH:mm").verifying("Invalid date selected!", date => date.after(new Date(dateTimeUtility.startOfDayMillis))),
      "session" -> nonEmptyText.verifying("Wrong session type specified!", session => session == "session 1" || session == "session 2"),
      "feedbackFormId" -> nonEmptyText,
      "topic" -> nonEmptyText,
      "meetup" -> boolean
    )(UpdateSessionInformation.apply)(UpdateSessionInformation.unapply)
  )

  def sessions(pageNumber: Int = 1): Action[AnyContent] = action.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber)
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
          .activeCount
          .map { count =>
            val pages = Math.ceil(count / 10D).toInt

            Ok(views.html.sessions.sessions(knolxSessions, pages, pageNumber))
          }
      }
  }

  def manageSessions(pageNumber: Int = 1): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber)
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
            .activeCount
            .map { count =>
              val pages = Math.ceil(count / 10D).toInt

              Ok(views.html.sessions.managesessions(sessions, pages, pageNumber))
            }
        }
      }
  }

  def create: Action[AnyContent] = userAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .map { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))

        Ok(views.html.sessions.createsession(createSessionForm, formIds))
      }
  }

  def createSession: Action[AnyContent] = userAction.async { implicit request =>
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
              .flatMap(_.headOption.fold {
                Future.successful(
                  BadRequest(views.html.sessions.createsession(createSessionForm.fill(createSessionInfo).withGlobalError("Email not valid!"), formIds))
                )
              } { userJson =>
                val userObjId = userJson._id.stringify
                val session = models.SessionInfo(userObjId, createSessionInfo.email.toLowerCase, BSONDateTime(createSessionInfo.date.getTime),
                  createSessionInfo.session, createSessionInfo.feedbackFormId, createSessionInfo.topic, createSessionInfo.meetup, rating = "",
                  cancelled = false, active = true)

                sessionsRepository.insert(session) flatMap { result =>
                  if (result.ok) {
                    Logger.info(s"Session for user ${createSessionInfo.email} successfully created")

                    (sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse] map {
                      case ScheduledSessionsRefreshed    =>
                        Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Session successfully created!")
                      case ScheduledSessionsNotRefreshed =>
                        Logger.error(s"Cannot refresh feedback form schedulers while creating session ${createSessionInfo.topic}")
                        InternalServerError("Something went wrong!")
                      case msg                           =>
                        Logger.error(s"Something went wrong when refreshing feedback form schedulers $msg while creating session ${createSessionInfo.topic}")
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

  def deleteSession(id: String, pageNumber: Int): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .delete(id)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete Knolx session with id $id")

        Future.successful(InternalServerError("Something went wrong!"))
      } { sessionJson =>
        Logger.info(s"Knolx session $id successfully deleted")

        (sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse] map {
          case ScheduledSessionsRefreshed    =>
            Redirect(routes.SessionsController.manageSessions(pageNumber)).flashing("message" -> "Session successfully Deleted!")
          case ScheduledSessionsNotRefreshed =>
            Logger.error(s"Cannot refresh feedback form schedulers while deleting session $id")
            InternalServerError("Something went wrong!")
          case msg                           =>
            Logger.error(s"Something went wrong when refreshing feedback form schedulers $msg while deleting session $id")
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
                sessionInformation.feedbackFormId, sessionInformation.topic, sessionInformation.meetup))
              Ok(views.html.sessions.updatesession(filledForm, formIds))
            }

        case None => Future.successful(Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Something went wrong!"))
      }
  }

  def updateSession(): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .flatMap { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))
        updateSessionForm.bindFromRequest.fold(
          formWithErrors => {
            Logger.error(s"Received a bad request for update session $formWithErrors")
            Future.successful(BadRequest(views.html.sessions.updatesession(formWithErrors, formIds)))
          },
          sessionUpdateInfo => {
            sessionsRepository
              .update(sessionUpdateInfo)
              .flatMap { result =>
                if (result.ok) {
                  Logger.info(s"Successfully updated session ${sessionUpdateInfo._id}")

                  (sessionsScheduler ? RefreshSessionsSchedulers) (5.seconds).mapTo[SessionsSchedulerResponse] map {
                    case ScheduledSessionsRefreshed    =>
                      Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Session successfully updated")
                    case ScheduledSessionsNotRefreshed =>
                      Logger.error(s"Cannot refresh feedback form schedulers while updating session ${sessionUpdateInfo._id}")
                      InternalServerError("Something went wrong!")
                    case msg                           =>
                      Logger.error(s"Something went wrong when refreshing feedback form schedulers $msg while updating session ${sessionUpdateInfo._id}")
                      InternalServerError("Something went wrong!")
                  }
                } else {
                  Logger.error(s"Something went wrong when updating a new Knolx session for user  ${sessionUpdateInfo._id}")
                  Future.successful(InternalServerError("Something went wrong!"))
                }
              }
          })
      }
  }

  def cancelScheduledSession(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (sessionsScheduler ? CancelScheduledSession(sessionId)) (5.seconds).mapTo[Boolean] map {
      case true  =>
        Redirect(routes.SessionsController.manageSessions(1))
          .flashing("message" -> "Scheduled feedback form successfully cancelled!")
      case false =>
        Redirect(routes.SessionsController.manageSessions(1))
          .flashing("message" -> "Either feedback form was already sent or Something went wrong while removing scheduled feedback form!")
    }
  }

  def scheduleSession(sessionId: String): Action[AnyContent] = action.async { implicit request =>
    (sessionsScheduler ? ScheduleSession(sessionId)) (5.seconds).mapTo[Boolean] map {
      case true  =>
        Redirect(routes.SessionsController.manageSessions(1))
          .flashing("message" -> "Feedback form successfully scheduled!")
      case false =>
        Redirect(routes.SessionsController.manageSessions(1))
          .flashing("message" -> "Something went wrong while scheduling feedback form!")
    }
  }

}

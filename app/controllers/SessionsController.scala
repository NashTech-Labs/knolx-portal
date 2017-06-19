package controllers

import java.util.Date
import javax.inject.{Inject, Singleton}

import models.{FeedbackFormsRepository, SessionsRepository, UsersRepository}
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Controller}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class CreateSessionInformation(email: String,
                                    date: Date,
                                    session: String,
                                    feedbackFormId: String,
                                    topic: String,
                                    meetup: Boolean)

case class UpdateSessionInformation(_id: String,
                                    date: Date,
                                    session: String,
                                    topic: String,
                                    meetup: Boolean = false)

case class KnolxSession(id: String,
                        userid: String,
                        date: Date,
                        session: String,
                        topic: String,
                        email: String,
                        meetup: Boolean,
                        cancelled: Boolean,
                        rating: String)

object SessionValues {
  val Sessions = Seq("session 1" -> "Session 1", "session 2" -> "Session 2")
}

@Singleton
class SessionsController @Inject()(val messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   feedbackFormsRepository: FeedbackFormsRepository) extends Controller with SecuredImplicit with I18nSupport {

  val usersRepo: UsersRepository = usersRepository

  val createSessionForm = Form(
    mapping(
      "email" -> email,
      "date" -> date.verifying("Invalid date selected!", date => date.after(new Date)),
      "session" -> nonEmptyText.verifying("Wrong session type specified!", session => session == "session 1" || session == "session 2"),
      "feedbackFormId" -> nonEmptyText,
      "topic" -> nonEmptyText,
      "meetup" -> boolean
    )(CreateSessionInformation.apply)(CreateSessionInformation.unapply)
  )
  val updateSessionForm = Form(
    mapping(
      "sessionId" -> nonEmptyText,
      "date" -> date.verifying("Invalid date selected!", date => date.after(new Date)),
      "session" -> nonEmptyText.verifying("Wrong session type specified!", session => session == "session 1" || session == "session 2"),
      "topic" -> nonEmptyText,
      "meetup" -> boolean
    )(UpdateSessionInformation.apply)(UpdateSessionInformation.unapply)
  )

  def sessions(pageNumber: Int = 1): Action[AnyContent] = Action.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber)
      .flatMap { sessionInfo =>
        val knolxSessions = sessionInfo map (session =>
          KnolxSession(session._id.stringify, session.userId, session.date, session.session, session.topic, session.email, session.meetup,
            session.cancelled, session.rating))

        sessionsRepository
          .activeCount
          .map { count =>
            val pages = Math.ceil(count / 10D).toInt

            Ok(views.html.sessions.sessions(knolxSessions, pages, pageNumber))
          }
      }
  }

  def manageSessions(pageNumber: Int = 1): Action[AnyContent] = AdminAction.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber)
      .flatMap { sessionsJson =>
        val knolxSessions = sessionsJson map (session =>
          KnolxSession(session._id.stringify,
            session.userId,
            session.date,
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

            Ok(views.html.sessions.managesessions(knolxSessions, pages, pageNumber))
          }
      }
  }

  def create: Action[AnyContent] = UserAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .map { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))

        Ok(views.html.sessions.createsession(createSessionForm, formIds))
      }
  }

  def createSession: Action[AnyContent] = UserAction.async { implicit request =>
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
                val session = models.SessionInfo(userObjId, createSessionInfo.email.toLowerCase, createSessionInfo.date, createSessionInfo.session,
                  createSessionInfo.feedbackFormId, createSessionInfo.topic, createSessionInfo.meetup, rating = "", cancelled = false, active = true)
                sessionsRepository.insert(session) map { result =>
                  if (result.ok) {
                    Logger.info(s"Session for user ${createSessionInfo.email} successfully created")
                    Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Session successfully created!")
                  } else {
                    Logger.error(s"Something went wrong when creating a new Knolx session for user ${createSessionInfo.email}")
                    InternalServerError("Something went wrong!")
                  }
                }
              })
          })
      }
  }

  def deleteSession(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    sessionsRepository
      .delete(id)
      .map(_.fold {
        Logger.error(s"Failed to delete knolx session with id $id")
        InternalServerError("Something went wrong!")
      } { sessionJson =>
        Logger.info(s"Knolx session $id successfully deleted")
        Ok
      })
  }

  def update(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    sessionsRepository
      .getById(id)
      .map {
        case Some(sessionInformation) =>
          val filledForm = updateSessionForm.fill(UpdateSessionInformation(sessionInformation._id.stringify,
            sessionInformation.date, sessionInformation.session, sessionInformation.topic, sessionInformation.meetup))

          Ok(views.html.sessions.updatesession(filledForm))

        case None => Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Something went wrong!")
      }
  }

  def updateSession(): Action[AnyContent] = AdminAction.async { implicit request =>
    updateSessionForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for update session $formWithErrors")
        Future.successful(BadRequest(views.html.sessions.updatesession(formWithErrors)))
      },
      sessionUpdateInfo => {
        sessionsRepository.update(sessionUpdateInfo) map { result =>
          if (result.ok) {
            Logger.info(s"Successfully updated session ${sessionUpdateInfo._id}")
            Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Session successfully updated")
          } else {
            Logger.error(s"Something went wrong when updating a new Knolx session for user  ${sessionUpdateInfo._id}")
            InternalServerError("Something went wrong!")
          }
        }
      })
  }

}

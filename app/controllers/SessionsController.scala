package controllers

import java.util
import javax.inject.{Inject, Singleton}

import controllers.SessionFields._
import models.{SessionsRepository, UsersRepository}
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsString, JsNumber, JsValue}
import play.api.mvc.{Action, AnyContent, Controller}
import reactivemongo.bson.{BSONObjectID, BSONDateTime, BSONDocument}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class CreateSessionInformation(email: String,
                                    date: util.Date,
                                    session: String,
                                    topic: String,
                                    meetup: Boolean)

case class KnolxSession(id: String,
                        date: util.Date,
                        session: String,
                        topic: String,
                        email: String,
                        meetup: Boolean,
                        cancelled: Boolean,
                        rating: String)

object SessionFields {
  val Email = "email"
  val Date = "date"
  val UserId = "user_id"
  val Session = "session"
  val Topic = "topic"
  val Meetup = "meetup"
  val Cancelled = "cancelled"
  val Rating = "rating"
  val Active = "active"
}

object SessionValues {
  val Sessions = Seq("session 1" -> "Session 1", "session 2" -> "Session 2")
}

@Singleton
class SessionsController @Inject()(val messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository) extends Controller with SecuredImplicit with I18nSupport {

  val usersRepo = usersRepository

  val createSessionForm = Form(
    mapping(
      "email" -> email,
      "date" -> date.verifying("Invalid date selected!", date => date.after(new util.Date)),
      "session" -> nonEmptyText.verifying("Wrong session type specified!", session => session == "session 1" || session == "session 2"),
      "topic" -> nonEmptyText,
      "meetup" -> boolean
    )(CreateSessionInformation.apply)(CreateSessionInformation.unapply)
  )

  def sessions: Action[AnyContent] = Action.async { implicit request =>
    sessionsRepository
      .sessions
      .map { sessionsJson =>
        val knolxSessions = sessionsJson map { session =>
          val knolxSessionAsMap = session.fields.toMap

          KnolxSession(
            knolxSessionAsMap.get("_id").map(_.validate[Map[String, String]].get("$oid")).getOrElse(""),
            new util.Date(knolxSessionAsMap.get(Date).map(_.validate[Map[String, Long]].get("$date")).getOrElse(System.currentTimeMillis)),
            knolxSessionAsMap.get(Session).flatMap(_.validate[String].asOpt).getOrElse(""),
            knolxSessionAsMap.get(Topic).flatMap(_.validate[String].asOpt).getOrElse(""),
            knolxSessionAsMap.get(Email).flatMap(_.validate[String].asOpt).getOrElse(""),
            knolxSessionAsMap.get(Meetup).flatMap(_.validate[Boolean].asOpt).getOrElse(false),
            knolxSessionAsMap.get(Cancelled).flatMap(_.validate[Boolean].asOpt).getOrElse(false),
            knolxSessionAsMap.get(Rating).flatMap(_.validate[String].asOpt).getOrElse(""))
        }

        Ok(views.html.sessions(knolxSessions))
      }
  }

  def manageSessions: Action[AnyContent] = AdminAction.async { implicit request =>
    sessionsRepository
      .sessions
      .map { sessionsJson =>
        val knolxSessions = sessionsJson map { session =>
          val knolxSessionAsMap = session.fields.toMap

          KnolxSession(
            knolxSessionAsMap.get("_id").map(_.validate[Map[String, String]].get("$oid")).getOrElse(""),
            new util.Date(knolxSessionAsMap.get(Date).map(_.validate[Map[String, Long]].get("$date")).getOrElse(System.currentTimeMillis)),
            knolxSessionAsMap.get(Session).flatMap(_.validate[String].asOpt).getOrElse(""),
            knolxSessionAsMap.get(Topic).flatMap(_.validate[String].asOpt).getOrElse(""),
            knolxSessionAsMap.get(Email).flatMap(_.validate[String].asOpt).getOrElse(""),
            knolxSessionAsMap.get(Meetup).flatMap(_.validate[Boolean].asOpt).getOrElse(false),
            knolxSessionAsMap.get(Cancelled).flatMap(_.validate[Boolean].asOpt).getOrElse(false),
            knolxSessionAsMap.get(Rating).flatMap(_.validate[String].asOpt).getOrElse(""))
        }

        Ok(views.html.managesessions(knolxSessions))
      }
  }

  def create: Action[AnyContent] = UserAction { implicit request =>
    Ok(views.html.createsession(createSessionForm))
  }

  def createSession: Action[AnyContent] = UserAction.async { implicit request =>
    createSessionForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for create session $formWithErrors")
        Future.successful(BadRequest(views.html.createsession(formWithErrors)))
      },
      sessionInfo => {
        usersRepository
          .getByEmail(sessionInfo.email.toLowerCase)
          .flatMap(_.headOption.fold {
            Future.successful(
              BadRequest(views.html.createsession(createSessionForm.fill(sessionInfo).withGlobalError("Email not valid!")))
            )
          } { userJson =>
            val userObjId = userJson.fields.toMap.get("_id").map(_.validate[Map[String, String]].get("$oid")).get

            sessionsRepository
              .create(
                BSONDocument(
                  UserId -> BSONDocument("$oid" -> userObjId),
                  Email -> sessionInfo.email.toLowerCase,
                  Date -> BSONDateTime(sessionInfo.date.getTime),
                  Session -> sessionInfo.session,
                  Topic -> sessionInfo.topic,
                  Meetup -> sessionInfo.meetup,
                  Rating -> "",
                  Cancelled -> false,
                  Active -> true)
              ).map { result =>
              if (result.ok) {
                Logger.info(s"Session for user ${sessionInfo.email} successfully created")
                Redirect(routes.SessionsController.create()).flashing("message" -> "Session successfully created!")
              } else {
                Logger.error(s"Something went wrong when creating a new Knolx session for user ${sessionInfo.email}")
                InternalServerError("Something went wrong!")
              }
            }
          })
      })
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

}

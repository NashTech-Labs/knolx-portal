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

  implicit val knolxSessionInfoFormat: OFormat[KnolxSession] = Json.format[KnolxSession]

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
      .map { sessionInfo =>
        val knolxSessions = sessionInfo map { session =>
          val contentAvailable = session.youtubeURL.isDefined || session.slideShareURL.isDefined
          KnolxSession(session._id.stringify,
            session.userId,
            new Date(session.date.value),
            session.session,
            session.topic,
            session.email,
            session.meetup,
            session.cancelled,
            "",
            dateString = new Date(session.date.value).toString,
            completed = new Date(session.date.value).before(new java.util.Date(dateTimeUtility.nowMillis)),
            expired = new Date(session.expirationDate.value)
              .before(new java.util.Date(dateTimeUtility.nowMillis)),
            contentAvailable = contentAvailable)
        }
        Ok(Json.toJson(knolxSessions))
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

        Ok(views.html.calendar.createSessionByUser(Some(createSessionInfo)))
      }
    } else {
      Future.successful(Ok(views.html.calendar.createSessionByUser(None)))
    }
  }

  def createSessionByUser: Action[AnyContent] = userAction.async { implicit request =>
    createSessionFormByUser.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for create session $formWithErrors")
        Future.successful(BadRequest(views.html.calendar.createSessionByUser(Some(createSessionFormByUser))))
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
  
}

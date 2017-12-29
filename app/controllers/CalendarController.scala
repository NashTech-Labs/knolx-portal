package controllers

import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import models._
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, number, optional}
import play.api.{Configuration, Logger}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CalendarController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   feedbackFormsRepository: FeedbackFormsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   configuration: Configuration,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("EmailManager") emailManager: ActorRef
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val knolxSessionInfoFormat: OFormat[KnolxSession] = Json.format[KnolxSession]

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
}

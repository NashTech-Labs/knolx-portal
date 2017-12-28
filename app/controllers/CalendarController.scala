package controllers

import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import models._
import play.api.Configuration
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import utilities.DateTimeUtility

// this is not an unused import contrary to what intellij suggests, do not optimize

@Singleton
class CalendarController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   feedbackFormsRepository: FeedbackFormsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   configuration: Configuration,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("EmailManager") emailManager: ActorRef,
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  def renderCalendarPage = Action {
    Ok(views.html.calendar.calendar)
  }
}

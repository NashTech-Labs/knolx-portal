package controllers

import java.util.Date
import javax.inject.Inject

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class FeedbackSessions(userId: String,
                            email: String,
                            date: Date,
                            session: String,
                            feedbackFormId: String,
                            topic: String,
                            meetup: Boolean,
                            rating: String,
                            cancelled: Boolean,
                            active: Boolean,
                            id: String,
                            expirationDate: String)

case class FeedbackForms(name: String,
                         questions: List[QuestionInformation],
                         active: Boolean = true,
                         id: String)

class FeedbackFormsResponseController @Inject()(messagesApi: MessagesApi,
                                                mailerClient: MailerClient,
                                                usersRepository: UsersRepository,
                                                feedbackRepository: FeedbackFormsRepository,
                                                sessionsRepository: SessionsRepository,
                                                dateTimeUtility: DateTimeUtility,
                                                controllerComponents: KnolxControllerComponents
                                               ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val questionInformationFormat: OFormat[QuestionInformation] = Json.format[QuestionInformation]
  implicit val FeedbackFormsFormat: OFormat[FeedbackForms] = Json.format[FeedbackForms]

  val usersRepo: UsersRepository = usersRepository

  def getFeedbackFormsForToday: Action[AnyContent] = userAction.async { implicit request =>
    sessionsRepository
      .activeSessions
      .flatMap { activeSessions =>
        if (activeSessions.nonEmpty) {
          val sessionFeedbackMappings = Future.sequence(activeSessions map { session =>
            feedbackRepository.getByFeedbackFormId(session.feedbackFormId) map {
              case Some(form) =>
                val sessionInformation =
                  FeedbackSessions(session.userId,
                    session.email,
                    new Date(session.date.value),
                    session.session,
                    session.feedbackFormId,
                    session.topic,
                    session.meetup,
                    session.rating,
                    session.cancelled,
                    session.active,
                    session._id.stringify,
                    new Date(session.expirationDate.value).toString)
                val questions = form.questions.map(questions => QuestionInformation(questions.question, questions.options))
                val associatedFeedbackFormInformation = FeedbackForms(form.name, questions, form.active, form._id.stringify)

                Some((sessionInformation, Json.toJson(associatedFeedbackFormInformation).toString))
              case None       =>
                Logger.info(s"No feedback form found correspond to feedback form id: ${session.feedbackFormId} for session id :${session._id}")
                None
            }
          })

          sessionFeedbackMappings.map(mappings => Ok(views.html.feedback.todaysfeedbacks(mappings.flatten, Nil)))
        } else {
          Logger.info("No active sessions found")
          immediatePreviousSessions.map(sessions => Ok(views.html.feedback.todaysfeedbacks(Nil, sessions)))
        }
      }
  }

  private def immediatePreviousSessions: Future[List[FeedbackSessions]] =
    sessionsRepository
      .immediatePreviousExpiredSessions
      .map { sessions =>
        sessions map (session =>
          FeedbackSessions(session.userId,
            session.email,
            new Date(session.date.value),
            session.session,
            session.feedbackFormId,
            session.topic,
            session.meetup,
            session.rating,
            session.cancelled,
            session.active,
            session._id.stringify,
            new Date(session.expirationDate.value).toString))
      }

}

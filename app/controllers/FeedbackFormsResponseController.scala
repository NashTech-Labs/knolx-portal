package controllers

import java.time.{LocalDate, Instant, LocalDateTime}
import java.util.Date
import javax.inject.Inject

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent, Controller}
import utilities.DateTimeUtility

import scala.annotation.tailrec
import scala.collection.immutable.TreeMap
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

class FeedbackFormsResponseController @Inject()(val messagesApi: MessagesApi,
                                                mailerClient: MailerClient,
                                                usersRepository: UsersRepository,
                                                feedbackRepository: FeedbackFormsRepository,
                                                sessionsRepository: SessionsRepository,
                                                dateTimeUtility: DateTimeUtility) extends Controller with SecuredImplicit with I18nSupport {

  implicit val questionInformationFormat: OFormat[QuestionInformation] = Json.format[QuestionInformation]
  implicit val FeedbackFormsFormat: OFormat[FeedbackForms] = Json.format[FeedbackForms]

  val usersRepo: UsersRepository = usersRepository

  def getFeedbackFormsForToday: Action[AnyContent] = UserAction.async { implicit request =>
    sessionsRepository.populatedStates.foreach(println)


    sessionsRepository
      .activeSessions
      .flatMap { sessions =>
        val (activeSessions, expired) = activeAndExpiredSessions(sessions)

        if (sessions.nonEmpty) {
          val sessionFeedbackMappings = Future.sequence(sessions map { session =>
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
          Future.successful(Ok(views.html.feedback.todaysfeedbacks(Nil, getImmediatePreviousSessions(expired))))
        }
      }
  }

  private def getImmediatePreviousSessions(expiredSessions: List[SessionInfo]) =
    TreeMap(expiredSessions.groupBy(session => dateTimeUtility.toLocalDate(session.date.value).getDayOfMonth).toSeq: _*)(implicitly[Ordering[Int]].reverse)
      .headOption
      .toList
      .flatMap { case (_, sessions) =>
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

  private def activeAndExpiredSessions(sessions: List[SessionInfo]): (List[SessionInfo], List[SessionInfo]) = {
    val currentDate = dateTimeUtility.localDateTimeIST

    @tailrec
    def check(allSessions: List[SessionInfo], active: List[SessionInfo], expired: List[SessionInfo]): (List[SessionInfo], List[SessionInfo]) =
      allSessions match {
        case Nil             => (active, expired)
        case session :: rest =>
          val expirationDate = dateTimeUtility.toLocalDateTime(session.expirationDate.value)

          if (currentDate.isAfter(expirationDate)) {
            check(rest, active, expired :+ session)
          } else {
            check(rest, active :+ session, expired)
          }
      }

    check(sessions, Nil, Nil)
  }

}

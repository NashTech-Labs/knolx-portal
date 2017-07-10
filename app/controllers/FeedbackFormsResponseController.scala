package controllers

import java.time.{Instant, LocalDateTime}
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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

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
                         _id: String)

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
    sessionsRepository
      .getSessionsTillNow
      .flatMap { sessions =>
        val (active, expired) = getActiveAndExpiredSessions(sessions)
        if (active.nonEmpty) {
          val sessionFeedbackMappings = Future.sequence(active.map { session =>
            feedbackRepository.getByFeedbackFormId(session.feedbackFormId).map {
              case Some(form) =>
                val sessionInformation = FeedbackSessions(session.userId,
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

              case None =>
                Logger.info(s"No feedback form found correspond to feedback form id: ${session.feedbackFormId} for session id :${session._id}")
                None
            }
          })
          sessionFeedbackMappings.map(mappings => Ok(views.html.feedback.todaysfeedbacks(mappings.flatten, getImmediatePreviousSessions(expired).flatten)))
        }
        else {
          Logger.info("No active Session for Feedback Found")
          Future.successful(Ok(views.html.feedback.todaysfeedbacks(Nil, getImmediatePreviousSessions(expired).flatten)))
        }
      }
  }

  private def getImmediatePreviousSessions(expiredSessions: List[SessionInfo]): List[Option[FeedbackSessions]] = {
    if (expiredSessions.nonEmpty) {
      val mostRecentSession :: _ = expiredSessions.reverse
      val immediateLastSessionDate = Instant.ofEpochMilli(mostRecentSession.date.value).atZone(dateTimeUtility.ISTZoneId).toLocalDate
      expiredSessions.map(session => {
        val sessionDate = Instant.ofEpochMilli(session.date.value).atZone(dateTimeUtility.ISTZoneId).toLocalDate
        if (sessionDate == immediateLastSessionDate) {

          val feedbackSession = FeedbackSessions(session.userId,
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
            new Date(session.expirationDate.value).toString
          )

          Some(feedbackSession)
        }
        else {
          None
        }
      })
    }
    else {
      List(None)
    }
  }

  private def getActiveAndExpiredSessions(sessions: List[SessionInfo]): (List[SessionInfo], List[SessionInfo]) = {
    val currentDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTimeUtility.nowMillis), dateTimeUtility.ISTZoneId)

    @tailrec
    def check(sessions: List[SessionInfo], active: List[SessionInfo], expired: List[SessionInfo]): (List[SessionInfo], List[SessionInfo]) = {
      sessions match {
        case Nil => (active, expired)
        case session :: rest =>
          val expiredDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.expirationDate.value), dateTimeUtility.ISTZoneId)
          if (currentDate.isAfter(expiredDate)) {
            check(rest, active, expired :+ session)
          }
          else {
            check(rest, active :+ session, expired)
          }
      }
    }

    check(sessions, Nil, Nil)
  }

}

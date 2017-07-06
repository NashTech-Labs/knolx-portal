package controllers

import java.time.{DayOfWeek, _}
import java.util.Date
import javax.inject.{Inject, Singleton}

import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, Controller}
import reactivemongo.bson.BSONObjectID
import utilities.DateTimeUtility

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class QuestionInformation(question: String, options: List[String])

case class FeedbackFormPreview(name: String, questions: List[QuestionInformation])

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
                            _id: BSONObjectID,
                            expirationDate: String)

case class FeedbackForms(name: String,
                         questions: List[QuestionInformation],
                         active: Boolean = true,
                         _id: BSONObjectID = BSONObjectID.generate)

case class UpdateFeedbackFormInformation(id: String, name: String, questions: List[QuestionInformation]) {

  def validateName: Option[String] =
    if (name.nonEmpty) {
      None
    } else {
      Some("Form name must not be empty!")
    }

  def validateForm: Option[String] =
    if (questions.flatMap(_.options).nonEmpty) {
      None
    } else {
      Some("Question must require at least 1 option!")
    }

  def validateQuestion: Option[String] =
    if (!questions.map(_.question).contains("")) {
      None
    } else {
      Some("Question must not be empty!")
    }

  def validateOptions: Option[String] =
    if (!questions.flatMap(_.options).contains("")) {
      None
    } else {
      Some("Options must not be empty!")
    }

}

case class FeedbackFormInformation(name: String, questions: List[QuestionInformation]) {

  def validateName: Option[String] =
    if (name.nonEmpty) {
      None
    } else {
      Some("Form name must not be empty!")
    }

  def validateForm: Option[String] =
    if (questions.flatMap(_.options).nonEmpty) {
      None
    } else {
      Some("Question must require at least 1 option!")
    }

  def validateQuestion: Option[String] =
    if (!questions.map(_.question).contains("")) {
      None
    } else {
      Some("Question must not be empty!")
    }

  def validateOptions: Option[String] =
    if (!questions.flatMap(_.options).contains("")) {
      None
    } else {
      Some("Options must not be empty!")
    }

}

@Singleton
class FeedbackFormsController @Inject()(val messagesApi: MessagesApi,
                                        mailerClient: MailerClient,
                                        usersRepository: UsersRepository,
                                        feedbackRepository: FeedbackFormsRepository,
                                        sessionsRepository: SessionsRepository,
                                        dateTimeUtility: DateTimeUtility) extends Controller with SecuredImplicit with I18nSupport {

  implicit val questionInformationFormat: OFormat[QuestionInformation] = Json.format[QuestionInformation]
  implicit val feedbackFormInformationFormat: OFormat[FeedbackFormInformation] = Json.format[FeedbackFormInformation]
  implicit val feedbackPreviewFormat: OFormat[FeedbackFormPreview] = Json.format[FeedbackFormPreview]
  implicit val updateFeedbackFormInformationFormat: OFormat[UpdateFeedbackFormInformation] = Json.format[UpdateFeedbackFormInformation]

  val usersRepo: UsersRepository = usersRepository

  def manageFeedbackForm(pageNumber: Int): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .paginate(pageNumber)
      .flatMap { feedbackForms =>
        val updateFormInformation = feedbackForms map { feedbackForm =>
          val questionInformation = feedbackForm.questions.map(question => QuestionInformation(question.question, question.options))

          UpdateFeedbackFormInformation(feedbackForm._id.stringify, feedbackForm.name, questionInformation)
        }

        feedbackRepository
          .activeCount
          .map { pages =>
            Ok(views.html.feedbackforms.managefeedbackforms(updateFormInformation, pageNumber, pages))
          }
      }
  }

  def feedbackForm: Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.feedbackforms.createfeedbackform())
  }

  def createFeedbackForm: Action[JsValue] = AdminAction.async(parse.json) { implicit request =>
    request.body.validate[FeedbackFormInformation].asOpt.fold {
      Logger.error(s"Received a bad request while creating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { feedbackFormInformation =>
      val formValid = feedbackFormInformation.validateName orElse feedbackFormInformation.validateForm orElse
        feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

      formValid.fold {
        val questions = feedbackFormInformation.questions.map(questionInformation => Question(questionInformation.question, questionInformation.options))

        feedbackRepository.insert(FeedbackForm(feedbackFormInformation.name, questions)) map { result =>
          if (result.ok) {
            Logger.info(s"Feedback form successfully created")
            Ok("Feedback form successfully created!")
          } else {
            Logger.error(s"Something went wrong when creating a feedback")
            InternalServerError("Something went wrong!")
          }
        }
      } { errorMessage =>
        Logger.error(s"Received a bad request for feedback form, ${request.body} $errorMessage")
        Future.successful(BadRequest(errorMessage))
      }
    }
  }

  def getFeedbackFormPreview(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .getByFeedbackFormId(id)
      .map {
        case Some(feedbackForm) =>
          val questions = feedbackForm.questions map (question => QuestionInformation(question.question, question.options))
          val feedbackPayload = FeedbackFormPreview(feedbackForm.name, questions)

          Ok(Json.toJson(feedbackPayload).toString)
        case None => NotFound("404! feedback form not found")
      }
  }

  def sendFeedbackForm(sessionId: String): Action[AnyContent] = AdminAction { implicit request =>
    val email =
      Email(subject = "Knolx Feedback Form",
        from = "sidharth@knoldus.com",
        to = List("sidharth@knoldus.com"),
        bodyHtml = None,
        bodyText = Some("Hello World"), replyTo = None)

    val emailSent = mailerClient.send(email)

    Ok(emailSent)
  }

  def update(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .getByFeedbackFormId(id)
      .map {
        case Some(feedForm: FeedbackForm) => Ok(views.html.feedbackforms.updatefeedbackform(feedForm, jsonCountBuilder(feedForm)))
        case None => Redirect(routes.SessionsController.manageSessions(1)).flashing("message" -> "Something went wrong!")
      }
  }

  def jsonCountBuilder(feedForm: FeedbackForm): String = {

    @tailrec
    def builder(questions: List[Question], json: List[String], count: Int): List[String] = {
      questions match {
        case Nil => json
        case head :: tail => builder(tail, json :+ s""""$count":"${head.options.size}"""", count + 1)
      }
    }

    s"{${builder(feedForm.questions, Nil, 0).mkString(",")}}"
  }

  def updateFeedbackForm: Action[JsValue] = AdminAction.async(parse.json) { implicit request =>
    request.body.validate[UpdateFeedbackFormInformation].asOpt.fold {
      Logger.error(s"Received a bad request while updating feedback form, ${request.body}")
      Future.successful(BadRequest("Malformed data!"))
    } { feedbackFormInformation =>
      val validatedForm =
        feedbackFormInformation.validateForm orElse feedbackFormInformation.validateName orElse
          feedbackFormInformation.validateOptions orElse feedbackFormInformation.validateQuestion

      validatedForm.fold {
        val questions = feedbackFormInformation.questions.map(questionInformation => Question(questionInformation.question, questionInformation.options))

        feedbackRepository.update(feedbackFormInformation.id, FeedbackForm(feedbackFormInformation.name, questions)) map { result =>
          if (result.ok) {
            Logger.info(s"Feedback form successfully updated")
            Ok("Feedback form successfully updated!")
          } else {
            Logger.error(s"Something went wrong when updated a feedback")
            InternalServerError("Something went wrong!")
          }
        }
      } { errorMessage =>
        Logger.error(s"Received a bad request for feedback form, ${request.body} $errorMessage")
        Future.successful(BadRequest(errorMessage))
      }
    }
  }

  def deleteFeedbackForm(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    feedbackRepository
      .delete(id)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete knolx feedback form with id $id")
        Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("errormessage" -> "Something went wrong!"))
      } { _ =>
        Logger.info(s"Knolx feedback form with id:  $id has been successfully deleted")
        Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("message" -> "Feedback form successfully deleted!"))
      })
  }

  def getFeedbackFormsForToday: Action[AnyContent] = UserAction.async { implicit request =>
    sessionsRepository
      .getSessionsTillNow
      .flatMap { sessions =>
        val (active, expired) = getActiveAndExpiredSessions(sessions)
        if (!active.isEmpty) {
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
                  session._id,
                  session.expirationDate.fold {
                    "OOps! Unable to Load!"
                  } { localDateTime =>
                    val date = Date.from(localDateTime.atZone(dateTimeUtility.ISTZoneId).toInstant());
                    date.toString
                  })

                val questions = form.questions.map(questions => QuestionInformation(questions.question, questions.options))
                val associatedFeedbackFormInformation = FeedbackForms(form.name, questions, form.active, form._id)
                Some((sessionInformation, associatedFeedbackFormInformation))

              case None => Logger.info(s"No feedback form found correspond to feedback form id: ${session.feedbackFormId} for session id :${session._id}")
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
    if (!expiredSessions.isEmpty) {
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
            session._id,
            session.expirationDate.fold {
              "OOps! Unable to Load!"
            } { localDateTime =>
              val date = Date.from(localDateTime.atZone(dateTimeUtility.ISTZoneId).toInstant());
              date.toString
            }
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
          val scheduledDate = Instant.ofEpochMilli(session.date.value).atZone(dateTimeUtility.ISTZoneId).toLocalDate
          val expirationDays = session.feedbackExpirationDays
          val sessionFeedbackExpirationDate = if (expirationDays > 0) {
            getCustomSessionExpirationDate(scheduledDate, session.feedbackExpirationDays)
          }
          else {
            getDefaultSessionExpirationDate(scheduledDate)
          }
          if (currentDate.isAfter(sessionFeedbackExpirationDate)) {
            check(rest, active, expired :+ session.copy(expirationDate = Some(sessionFeedbackExpirationDate)))
          }
          else {
            check(rest, active :+ session.copy(expirationDate = Some(sessionFeedbackExpirationDate)), expired)
          }
      }
    }

    check(sessions, Nil, Nil)
  }

  private def getDefaultSessionExpirationDate(scheduledDate: LocalDate): LocalDateTime = {
    val feedbackExpireOnWorkingDays: List[DayOfWeek] = List(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
    val dayOfTheWeek = scheduledDate.getDayOfWeek
    val tillEndOfTheDay = LocalTime.of(23, 59, 59, 9999)

    if (feedbackExpireOnWorkingDays.contains(dayOfTheWeek)) {
      LocalDateTime.of(scheduledDate.plusDays(1), tillEndOfTheDay)
    }
    else {
      if (dayOfTheWeek == DayOfWeek.FRIDAY) {
        LocalDateTime.of(scheduledDate.plusDays(3), tillEndOfTheDay)
      }
      else {
        //SATURDAY
        LocalDateTime.of(scheduledDate.plusDays(2), tillEndOfTheDay)
      }
    }
  }

  private def getCustomSessionExpirationDate(scheduledDate: LocalDate, days: Int): LocalDateTime = {
    val tillEndOfTheDay = LocalTime.of(23, 59, 59, 9999)
    LocalDateTime.of(scheduledDate.plusDays(days), tillEndOfTheDay)
  }

}

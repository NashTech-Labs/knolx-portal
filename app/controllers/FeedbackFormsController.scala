package controllers

import java.util.{Calendar, Date}
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
                            expired: Boolean,
                            expirationStatusAssociatedDate: Date)

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
      } { sessionJson =>
        Logger.info(s"Knolx feedback form with id:  $id has been successfully deleted")
        Future.successful(Redirect(routes.FeedbackFormsController.manageFeedbackForm(1)).flashing("message" -> "Feedback form successfully deleted!"))
      })
  }

  def getFeedbackFormsForToday: Action[AnyContent] = Action.async { implicit request =>
    sessionsRepository
      .getSessionsTillNow
      .flatMap { sessions =>
        val sessionFeedbackMappings = Future.sequence(sessions.map { session =>
          feedbackRepository.getByFeedbackFormId(session.feedbackFormId).map {
            case Some(form) =>
              val (expired, expirationStatusAssociatedDate) = isExpired(session.date.value)
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
                expired,
                expirationStatusAssociatedDate)

              val questions = form.questions.map(questions => QuestionInformation(questions.question, questions.options))
              val associatedFeedbackFormInformation = FeedbackForms(form.name, questions, form.active, form._id)
              if (!expired) {
                Some((sessionInformation, Some(associatedFeedbackFormInformation)))
              }
              else {
                Some((sessionInformation, None))
              }
            case None => None
          }
        })
        sessionFeedbackMappings.map(mappings => Ok(views.html.feedback.todaysfeedbacks(mappings.flatten)))
      }
  }

  def isExpired(sessionScheduledDate: Long): (Boolean, Date) = {
    //sun, mon, tue, wed ,thu
    val feedbackExpireOnWorkingDays = List(1, 2, 3, 4, 5)

    val currentDate = new Date(dateTimeUtility.nowMillis)
    val scheduledDate = new Date(sessionScheduledDate)

    val calendar = Calendar.getInstance()
    calendar.setTime(scheduledDate)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)

    if (feedbackExpireOnWorkingDays.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
      calendar.add(Calendar.DAY_OF_WEEK, 1)
      //current date < sessionDate + 1 day(end of the day) then display
    }
    else {
      //friday ---> if current date < sessionDate + 3 days(end of the day) then display
      if (calendar.get(Calendar.DAY_OF_WEEK) == 6) {
        calendar.add(Calendar.DAY_OF_WEEK, 3)
      }
      //saturday ---> if current date < sessionDate + 2 days(end of the day) then display
      else {
        calendar.add(Calendar.DAY_OF_WEEK, 2)
      }
    }

    if (currentDate.compareTo(calendar.getTime) <= 0) {
      (false, calendar.getTime)
    }
    else {
      (true, calendar.getTime)
    }
  }

}

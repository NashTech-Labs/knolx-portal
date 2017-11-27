package controllers

import java.time._
import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import actors.SessionsScheduler._
import actors.UsersBanScheduler._
import akka.actor.ActorRef
import akka.pattern.ask
import controllers.EmailHelper._
import models._
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, Result}
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.collection.immutable.IndexedSeq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

case class CreateSessionInformation(email: String,
                                    date: Date,
                                    session: String,
                                    category: String,
                                    subCategory: String,
                                    feedbackFormId: String,
                                    topic: String,
                                    feedbackExpirationDays: Int,
                                    meetup: Boolean)

case class UpdateSessionInformation(id: String,
                                    date: Date,
                                    session: String,
                                    category: String,
                                    subCategory: String,
                                    feedbackFormId: String,
                                    topic: String,
                                    feedbackExpirationDays: Int,
                                    youtubeURL: Option[String],
                                    slideShareURL: Option[String],
                                    cancelled: Boolean,
                                    meetup: Boolean = false)

case class KnolxSession(id: String,
                        userId: String,
                        date: Date,
                        session: String,
                        topic: String,
                        email: String,
                        meetup: Boolean,
                        cancelled: Boolean,
                        rating: String,
                        feedbackFormScheduled: Boolean = false,
                        dateString: String = "",
                        completed: Boolean = false,
                        expired: Boolean = false)

case class SessionEmailInformation(email: Option[String], page: Int)
case class ModelsCategoryInformation(_id: String, categoryName: String, subCategory: List[String])
case class SessionSearchResult(sessions: List[KnolxSession],
                               pages: Int,
                               page: Int,
                               keyword: String)

object SessionValues {
  val Sessions: IndexedSeq[(String, String)] = 1 to 5 map (number => (s"session $number", s"Session $number"))
}

@Singleton
class SessionsController @Inject()(messagesApi: MessagesApi,
                                   usersRepository: UsersRepository,
                                   sessionsRepository: SessionsRepository,
                                   categoriesRepository: CategoriesRepository,
                                   feedbackFormsRepository: FeedbackFormsRepository,
                                   dateTimeUtility: DateTimeUtility,
                                   controllerComponents: KnolxControllerComponents,
                                   @Named("SessionsScheduler") sessionsScheduler: ActorRef,
                                   @Named("UsersBanScheduler") usersBanScheduler: ActorRef
                                  ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val knolxSessionInfoFormat: OFormat[KnolxSession] = Json.format[KnolxSession]
  implicit val sessionSearchResultInfoFormat: OFormat[SessionSearchResult] = Json.format[SessionSearchResult]
  implicit val modelsCategoriesFormat: OFormat[ModelsCategoryInformation] = Json.format[ModelsCategoryInformation]

  val sessionSearchForm = Form(
    mapping(
      "email" -> optional(nonEmptyText),
      "page" -> number.verifying("Invalid feedback form expiration days selected", _ >= 1)
    )(SessionEmailInformation.apply)(SessionEmailInformation.unapply)
  )

  val createSessionForm = Form(
    mapping(
      "email" -> email.verifying("Invalid Email", email => isValidEmail(email)),
      "date" -> date("yyyy-MM-dd'T'HH:mm", dateTimeUtility.ISTTimeZone)
        .verifying("Invalid date selected!", date => date.after(new Date(dateTimeUtility.startOfDayMillis))),
      "session" -> nonEmptyText.verifying("Wrong session type specified!",
        session => SessionValues.Sessions.map { case (value, _) => value }.contains(session)),
      "category" -> text.verifying("Please attach a category", !_.isEmpty),
      "subCategory" -> text.verifying("Please attach a sub-category", !_.isEmpty),
      "feedbackFormId" -> text.verifying("Please attach a feedback form template", !_.isEmpty),
      "topic" -> nonEmptyText,
      "feedbackExpirationDays" -> number.verifying("Invalid feedback form expiration days selected", number => number >= 0 && number <= 31),
      "meetup" -> boolean
    )(CreateSessionInformation.apply)(CreateSessionInformation.unapply)
  )
  val updateSessionForm = Form(
    mapping(
      "sessionId" -> nonEmptyText,
      "date" -> date("yyyy-MM-dd'T'HH:mm", dateTimeUtility.ISTTimeZone),
      "session" -> nonEmptyText.verifying("Wrong session type specified!",
        session => SessionValues.Sessions.map { case (value, _) => value }.contains(session)),
      "category" -> text.verifying("Please attach a category", !_.isEmpty),
      "subCategory" -> text.verifying("Please attach a sub-category", !_.isEmpty),
      "feedbackFormId" -> text.verifying("Please attach a feedback form template", !_.isEmpty),
      "topic" -> nonEmptyText,
      "feedbackExpirationDays" -> number.verifying("Invalid feedback form expiration days selected, " +
        "must be in range 1 to 31", number => number >= 0 && number <= 31),
      "youtubeURL" -> optional(nonEmptyText),
      "slideShareURL" -> optional(nonEmptyText),
      "cancelled" -> boolean,
      "meetup" -> boolean
    )(UpdateSessionInformation.apply)(UpdateSessionInformation.unapply)
  )


  def sessions(pageNumber: Int = 1, keyword: Option[String] = None): Action[AnyContent] = action.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber, keyword)
      .flatMap { sessionInfo =>
        val knolxSessions = sessionInfo map { session =>
          KnolxSession(session._id.stringify,
            session.userId,
            new Date(session.date.value),
            session.session,
            session.topic,
            session.email,
            session.meetup,
            session.cancelled,
            "",
            completed = new Date(session.date.value).before(new java.util.Date(dateTimeUtility.nowMillis)),
            expired = new Date(session.expirationDate.value)
              .before(new java.util.Date(dateTimeUtility.nowMillis)))
        }

        sessionsRepository
          .activeCount(keyword)
          .map { count =>
            val pages = Math.ceil(count / 10D).toInt
            Ok(views.html.sessions.sessions(knolxSessions, pages, pageNumber))
          }
      }
  }

  def searchSessions: Action[AnyContent] = action.async { implicit request =>
    sessionSearchForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage ==> $formWithErrors")
        Future.successful(BadRequest(" OOps! Invalid value encountered !"))
      },
      sessionInformation => {
        sessionsRepository
          .paginate(sessionInformation.page, sessionInformation.email)
          .flatMap { sessionInfo =>
            val knolxSessions = sessionInfo map (session =>
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
                  .before(new java.util.Date(dateTimeUtility.nowMillis))))

            sessionsRepository
              .activeCount(sessionInformation.email)
              .map { count =>
                val pages = Math.ceil(count / 10D).toInt

                Ok(Json.toJson(SessionSearchResult(knolxSessions, pages, sessionInformation.page, sessionInformation.email.getOrElse(""))).toString)
              }
          }
      })
  }

  def manageSessions(pageNumber: Int = 1, keyword: Option[String] = None): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .paginate(pageNumber, keyword)
      .flatMap { sessionsInfo =>
        val knolxSessions =
          sessionsInfo map (sessionInfo =>
            KnolxSession(sessionInfo._id.stringify,
              sessionInfo.userId,
              new Date(sessionInfo.date.value),
              sessionInfo.session,
              sessionInfo.topic,
              sessionInfo.email,
              sessionInfo.meetup,
              sessionInfo.cancelled,
              sessionInfo.rating,
              completed = new Date(sessionInfo.date.value).before(new java.util.Date(dateTimeUtility.nowMillis)),
              expired = new Date(sessionInfo.expirationDate.value)
                .before(new java.util.Date(dateTimeUtility.nowMillis))))

        val eventualScheduledFeedbackForms =
          (sessionsScheduler ? GetScheduledSessions) (5.seconds).mapTo[ScheduledSessions]

        val eventualKnolxSessions = eventualScheduledFeedbackForms map { scheduledFeedbackForms =>
          knolxSessions map { session =>
            val scheduled = scheduledFeedbackForms.sessionIds.contains(session.id)

            session.copy(feedbackFormScheduled = scheduled)
          }
        }

        eventualKnolxSessions flatMap { sessions =>
          sessionsRepository
            .activeCount(keyword)
            .map { count =>
              val pages = Math.ceil(count / 10D).toInt
              Ok(views.html.sessions.managesessions(sessions, pages, pageNumber))
            }
        }
      }
  }

  def searchManageSession: Action[AnyContent] = adminAction.async { implicit request =>
    sessionSearchForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage ==> $formWithErrors")
        Future.successful(BadRequest(" OOps! Invalid value encountered !"))
      },
      sessionInformation => {
        sessionsRepository
          .paginate(sessionInformation.page, sessionInformation.email)
          .flatMap { sessionInfo =>
            val knolxSessions = sessionInfo map (sessionInfo =>
              KnolxSession(sessionInfo._id.stringify,
                sessionInfo.userId,
                new Date(sessionInfo.date.value),
                sessionInfo.session,
                sessionInfo.topic,
                sessionInfo.email,
                sessionInfo.meetup,
                sessionInfo.cancelled,
                sessionInfo.rating,
                dateString = new Date(sessionInfo.date.value).toString,
                completed = new Date(sessionInfo.date.value).before(new java.util.Date(dateTimeUtility.nowMillis)),
                expired = new Date(sessionInfo.expirationDate.value)
                  .before(new java.util.Date(dateTimeUtility.nowMillis))
              ))

            val eventualScheduledFeedbackForms =
              (sessionsScheduler ? GetScheduledSessions) (5.seconds).mapTo[ScheduledSessions]

            val eventualKnolxSessions = eventualScheduledFeedbackForms map { scheduledFeedbackForms =>
              knolxSessions map { session =>
                val scheduled = scheduledFeedbackForms.sessionIds.contains(session.id)
                session.copy(feedbackFormScheduled = scheduled)
              }
            }
            eventualKnolxSessions flatMap { sessions =>
              sessionsRepository
                .activeCount(sessionInformation.email)
                .map { count =>
                  val pages = Math.ceil(count / 10D).toInt

                  Ok(Json.toJson(SessionSearchResult(sessions, pages, sessionInformation.page, sessionInformation.email.getOrElse(""))).toString)
                }
            }
          }
      })
  }

  def create: Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .map { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))
        Ok(views.html.sessions.createsession(createSessionForm, formIds))
      }
  }

  def createSession: Action[AnyContent] = adminAction.async { implicit request =>
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
              .flatMap(_.fold {
                Future.successful(
                  BadRequest(views.html.sessions.createsession(createSessionForm.fill(createSessionInfo).withGlobalError("Email not valid!"), formIds)))
              } { userJson =>
                val expirationDateMillis = sessionExpirationMillis(createSessionInfo.date, createSessionInfo.feedbackExpirationDays)
                val session = models.SessionInfo(userJson._id.stringify, createSessionInfo.email.toLowerCase,
                  BSONDateTime(createSessionInfo.date.getTime), createSessionInfo.session, createSessionInfo.category,
                  createSessionInfo.subCategory, createSessionInfo.feedbackFormId,
                  createSessionInfo.topic, createSessionInfo.feedbackExpirationDays, createSessionInfo.meetup, rating = "",
                  0, cancelled = false, active = true, BSONDateTime(expirationDateMillis), None, None)
                sessionsRepository.insert(session) flatMap { result =>
                  if (result.ok) {
                    Logger.info(s"Session for user ${createSessionInfo.email} successfully created")
                    sessionsScheduler ! RefreshSessionsSchedulers
                    Future.successful(Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Session successfully created!"))
                  } else {
                    Logger.error(s"Something went wrong when creating a new Knolx session for user ${createSessionInfo.email}")
                    Future.successful(InternalServerError("Something went wrong!"))
                  }
                }
              })
          })
      }
  }

  private def sessionExpirationMillis(date: Date, customDays: Int): Long =
    if (customDays > 0) {
      customSessionExpirationMillis(date, customDays)
    } else {
      defaultSessionExpirationMillis(date)
    }

  private def defaultSessionExpirationMillis(date: Date): Long = {
    val scheduledDate = dateTimeUtility.toLocalDateTimeEndOfDay(date)

    val expirationDate = scheduledDate.getDayOfWeek match {
      case DayOfWeek.FRIDAY   => scheduledDate.plusDays(4)
      case DayOfWeek.SATURDAY => scheduledDate.plusDays(3)
      case _: DayOfWeek       => scheduledDate.plusDays(1)
    }

    dateTimeUtility.toMillis(expirationDate)
  }

  private def customSessionExpirationMillis(date: Date, days: Int): Long = {
    val scheduledDate = dateTimeUtility.toLocalDateTimeEndOfDay(date)
    val expirationDate = scheduledDate.plusDays(days)

    dateTimeUtility.toMillis(expirationDate)
  }

  def deleteSession(id: String, pageNumber: Int): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .delete(id)
      .flatMap(_.fold {
        Logger.error(s"Failed to delete Knolx session with id $id")
        Future.successful(InternalServerError("Something went wrong!"))
      } { _ =>
        Logger.info(s"Knolx session $id successfully deleted")
        sessionsScheduler ! RefreshSessionsSchedulers
        Future.successful(Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Session successfully Deleted!"))
      })
  }

  def update(id: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsRepository
      .getById(id)
      .flatMap {
        case Some(sessionInformation) =>
          feedbackFormsRepository
            .getAll
            .map { feedbackForms =>
              val formIds = feedbackForms.map(form => (form._id.stringify, form.name))
              val filledForm = updateSessionForm.fill(UpdateSessionInformation(sessionInformation._id.stringify,
                new Date(sessionInformation.date.value), sessionInformation.session, sessionInformation.category, sessionInformation.subCategory,
                sessionInformation.feedbackFormId, sessionInformation.topic, sessionInformation.feedbackExpirationDays,
                sessionInformation.youtubeURL, sessionInformation.slideShareURL, sessionInformation.cancelled, sessionInformation.meetup))
              Ok(views.html.sessions.updatesession(filledForm, formIds))
            }

        case None => Future.successful(Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Something went wrong!"))
      }
  }

  def updateSession(): Action[AnyContent] = adminAction.async { implicit request =>
    feedbackFormsRepository
      .getAll
      .flatMap { feedbackForms =>
        val formIds = feedbackForms.map(form => (form._id.stringify, form.name))
        updateSessionForm.bindFromRequest.fold(
          formWithErrors => {
            Logger.error(s"Received a bad request for getByEmail session $formWithErrors")
            Future.successful(BadRequest(views.html.sessions.updatesession(formWithErrors, formIds)))
          },
          updateSessionInfo => {
            val expirationMillis = sessionExpirationMillis(updateSessionInfo.date, updateSessionInfo.feedbackExpirationDays)
            val updatedSession = UpdateSessionInfo(updateSessionInfo, BSONDateTime(expirationMillis))
            sessionsRepository
              .update(updatedSession)
              .flatMap { result =>
                if (result.ok) {
                  Logger.info(s"Successfully updated session ${updateSessionInfo.id}")
                  sessionsScheduler ! RefreshSessionsSchedulers
                  Logger.error(s"Cannot refresh feedback form actors while updating session ${updateSessionInfo.id}")
                  Future.successful(Redirect(routes.SessionsController.manageSessions(1, None)).flashing("message" -> "Session successfully updated"))
                } else {
                  Logger.error(s"Something went wrong when updating a new Knolx session for user  ${updateSessionInfo.id}")
                  Future.successful(InternalServerError("Something went wrong!"))
                }
              }
          })
      }
  }

  def cancelScheduledSession(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    (sessionsScheduler ? CancelScheduledSession(sessionId)) (5.seconds).mapTo[Boolean] map {
      case true  =>
        Redirect(routes.SessionsController.manageSessions(1, None))
          .flashing("message" -> "Scheduled feedback form successfully cancelled!")
      case false =>
        Redirect(routes.SessionsController.manageSessions(1, None))
          .flashing("message" -> "Either feedback form was already sent or Something went wrong while removing scheduled feedback form!")
    }
  }

  def scheduleSession(sessionId: String): Action[AnyContent] = adminAction.async { implicit request =>
    sessionsScheduler ! ScheduleSession(sessionId)
    Future.successful(Redirect(routes.SessionsController.manageSessions(1, None))
      .flashing("message" -> "Feedback form schedule initiated"))
  }

  def shareContent(id: String): Action[AnyContent] = action.async { implicit request =>
    if (id.length != 24) {
      Future.successful(Redirect(routes.SessionsController.sessions(1, None)).flashing("message" -> "Session Not Found"))
    } else {
      val eventualMaybeSession: Future[Option[SessionInfo]] = sessionsRepository.getById(id)
      eventualMaybeSession.flatMap(maybeSession =>
        maybeSession.fold(Future.successful(Redirect(routes.SessionsController.sessions(1, None)).flashing("message" -> "Session Not Found")))
        (session => Future.successful(Ok(views.html.sessions.sessioncontent(session)))))
    }
  }

  def renderCategoryPage: Action[AnyContent] = adminAction.async { implicit request =>
    Logger.info("-------------------------render category Page")
    categoriesRepository.getCategories.map {
      Logger.info("-------------------------Inside render category page")
      category =>
        Logger.info("--------------------------Caetgory List " + category)
        Ok(views.html.category(category))
    }
  }

  def addPrimaryCategory(categoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    Logger.info("Inside add primary category")
    if (categoryName.trim().isEmpty) {
      Future.successful(BadRequest("Primary category cannot be empty"))
    } else {
      categoriesRepository.getCategories.flatMap { result =>
        if (result.exists(category => category.categoryName.toLowerCase.equals(categoryName.toLowerCase))) {
          Future.successful(BadRequest("Primary category already exits"))
        } else {
          categoriesRepository.insertCategory(categoryName).map { result =>
            if (result.ok) {
              Ok("Primary category was successfully added")
            } else {
              BadRequest("Primary category cannot be added due to some error")
            }
          }
        }
      }
    }
  }

  def addSubCategory(categoryName: String, subCategory: String): Action[AnyContent] = adminAction.async { implicit request =>
    Logger.info("Inside add Sub category")
    if (subCategory.trim().isEmpty) {
      Future.successful(BadRequest("Subcategory cannot be empty"))
    } else {
      categoriesRepository.getCategories.flatMap { result =>
        result.find {
          _.categoryName == categoryName
        }
          .fold {
            Future.successful(BadRequest("No primary category found."))
          } { categoryInfo =>
            val newSubCategory = categoryInfo.subCategory.find(_.toLowerCase == subCategory.toLowerCase)
            newSubCategory.fold {
              val subCategoryInfo = CategoryInfo(categoryName, List(subCategory), categoryInfo._id)
              Logger.error("------>" + subCategoryInfo + "-------------->" + categoryInfo)
              categoriesRepository.upsert(subCategoryInfo).map { result =>

                Logger.info("Inside add sub category1 " + result)
                if (result.ok) {
                  Ok("Sub-category was successfully added")
                } else {
                  Logger.info("Inside bad request")
                  BadRequest("Unsuccessful sub-category added")
                }
              }
            } { _ =>
              Future.successful(BadRequest("Error ! Sub-category already exist"))
            }
          }
      }
    }
  }

  def modifyPrimaryCategory(categoryId: String, newCategoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    Logger.info("Inside modify primary category")
    if (newCategoryName.trim().isEmpty) {
      Future.successful(BadRequest("Modify primary category cannot be empty"))
    } else {
      Logger.info("Old category name = " + categoryId)
      Logger.info("new category name = " + newCategoryName)
      categoriesRepository.getCategories.flatMap { result =>
        val category = result.filter( c => c._id.stringify==categoryId).head
          sessionsRepository.updateCategoryOnChange(category.categoryName, newCategoryName).flatMap { session =>
            if(session.ok) {
              categoriesRepository.modifyPrimaryCategory(category._id.stringify, newCategoryName).map { result =>
                if (result.ok) {
                  Ok("Primary category was successfully modified")
                } else {
                  Logger.info("Error Inside Sessions Controller")
                  BadRequest("Unsuccessfully attempt to modify primary category")
                }
              }
            } else{
              Future.successful(BadRequest("Update on session table was unsuccessful"))
            }
          }
        }
      }
    }

  def modifySubCategory(categoryName: String, oldSubCategoryName: String,
                        newSubCategoryName: String): Action[AnyContent] = adminAction.async { implicit request =>
    Logger.info("----------------------Inside modifySubCategory")
    if (newSubCategoryName.trim().isEmpty) {
      Future.successful(BadRequest("Modify sub-category cannot be empty"))
    } else {
      Logger.info("Before modifying sub category in session table")
      categoriesRepository.getCategories.flatMap {
        categories =>
          val subCategoryList = categories.filter {
            category => category.categoryName.toLowerCase == categoryName.toLowerCase
          }.flatMap(_.subCategory)

          val check = subCategoryList.contains(newSubCategoryName)
          Logger.error("list--->" + subCategoryList + "new ------>  " + newSubCategoryName.toLowerCase + "---check-->  " + check)
          if (check) {
            Future.successful(BadRequest("Sub category already exits"))
          } else {
            sessionsRepository.updateSubCategoryOnChange(oldSubCategoryName, newSubCategoryName).flatMap { session =>
              Logger.info("Modifying sub category in session table ")
              if (session.ok) {
                categoriesRepository.modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName).map {
                  result =>
                    if (result.ok) {
                      Logger.info("--------------------------- Returning Ok")
                      Ok("Successfully Modified sub category")
                    } else {
                      Logger.info("--------------------------- Returning Bad Request")
                      BadRequest("Got an error while modifying sub category")
                    }
                }
              } else {
                Future.successful(BadRequest("Got an error on updating session table"))
              }
            }
          }
      }
    }
  }

  def deletePrimaryCategory(categoryId: String): Action[AnyContent] = superUserAction.async { implicit request =>
    Logger.info("Inside delete primary category" + categoryId)
    if (categoryId.trim().isEmpty) {
      Future.successful(BadRequest("Please select a valid primary category"))
    } else {
      categoriesRepository.getCategories.flatMap {
        categories =>
          Logger.info("List of categories = " + categories)
          Logger.info("CategoryId = " + categoryId)
          val category = categories.find {
            c =>
              Logger.info("current category id is = " + c._id + " and is being compared with categoryId" + categoryId)
              c._id.toString() == categoryId
          }
          category.fold {
            Future.successful(BadRequest("No such primary category exists"))
          } { category =>
            val subCategoryList = category.subCategory
            Logger.info("Primary category with its sub category " + subCategoryList)
            if (subCategoryList.isEmpty) {
              Logger.info("Update Session Table on category delete")
              sessionsRepository.updateCategoryOnChange(category.categoryName, "").flatMap { session =>
                if (session.ok) {
                  Logger.info("Delete primary category on delete")
                  categoriesRepository.deletePrimaryCategory(category._id.stringify).map { result =>
                    if (result.ok) {
                      Ok("Primary category was successfully deleted")
                    } else {
                      BadRequest("Got an error while deleting")
                    }
                  }
                } else {
                  Future.successful(BadRequest("Got an error in session table"))
                }
              }
            } else {
              Future.successful(BadRequest("First delete all its sub category"))
            }
          }
      }
    }
  }

  def getSubCategoryByPrimaryCategory(categoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    if (categoryName.trim().isEmpty) {
      Future.successful(BadRequest("Please select a valid primary category"))
    } else {
      categoriesRepository.getCategories.map {
        categories =>
          val subCategoryList = categories.filter {
            category => category.categoryName == categoryName
          }.flatMap(_.subCategory)
          Logger.info("Primary category with its sub category " + subCategoryList)

          if(subCategoryList.isEmpty) {
            BadRequest(Json.toJson(subCategoryList).toString)
          } else {
            Ok(Json.toJson(subCategoryList).toString())
          }
      }
    }
  }

  def deleteSubCategory(categoryName: String, subCategory: String): Action[AnyContent] = adminAction.async { implicit request =>
    if (subCategory.trim.isEmpty) {
      Future.successful(BadRequest("Sub-category cannot be empty"))
    } else {
      Logger.info(s"..........Before session repository $categoryName $subCategory")
      sessionsRepository.updateSubCategoryOnChange(subCategory,"").flatMap { sessions =>
        Logger.info("Inside session repository")
        if (sessions.ok) {
          Logger.info(".............. Before categories repository")
          categoriesRepository.deleteSubCategory(categoryName, subCategory).flatMap { result =>
            if (result.ok) {
              Future.successful(Ok("Sub-category was successfully deleted"))
            } else {
              Future.successful(BadRequest("Something went wrong! unable to delete category"))
            }
          }
        } else {
          Future.successful(BadRequest("Got an error while deleting"))
        }
      }
    }
  }

  def getTopicsBySubCategory(categoryName: String, subCategory: String): Action[AnyContent] = adminAction.async { implicit request =>
    if (subCategory.trim.isEmpty) {
      Future.successful(BadRequest("Please select a valid sub-category"))
    } else {
      Logger.info("category name = " + categoryName)
      Logger.info("Sub category name =" + subCategory)
      sessionsRepository.sessions.map { sessionInformation =>
        val sessionTopicList = sessionInformation.filter {
          session => session.subCategory == subCategory && session.category == categoryName
        }.map {
          _.topic
        }
        Logger.info("Inside get topics by subCategory = " + sessionTopicList)
        Ok(Json.toJson(sessionTopicList).toString())
      }
    }
  }

  def getCategory: Action[AnyContent] = action.async { implicit request =>
    Logger.info("Inside get category of controller")
    categoriesRepository.getCategories.map { categories =>
      val listOfCategoryInfo = categories.map(category => ModelsCategoryInformation(category._id.stringify, category.categoryName, category.subCategory))
      Ok(Json.toJson(listOfCategoryInfo).toString)
    }
  }

}

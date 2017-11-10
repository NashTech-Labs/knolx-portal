package controllers

import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import models._
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.play.json.BSONFormats.BSONDateTimeFormat

// Knolx related analytics classes
case class SubCategoryInformation(subCategoryName: String, totalSessionSubCategory: Int)
case class CategoryInformation(categoryName: String, totalSessionCategory: Int, subCategoryInfo: List[SubCategoryInformation])
case class KnolxSessionInformation(totalSession: Int, categoryInformation: List[CategoryInformation])

case class KnolxMonthlyInfo(monthName: String, total: Int)
case class KnolxAnalysisDateRange(startDate: String, endDate: String)

// User knolx related analytics classes
case class FilterSessions(email: String, startDateString: String, endDateString: String)
case class FilterUserSessionInformation(email: Option[String], startDate: Date, endDate: Date)

@Singleton
class KnolxAnalysisController @Inject()(messagesApi: MessagesApi,
                                        usersRepository: UsersRepository,
                                        sessionsRepository: SessionsRepository,
                                        feedbackFormsRepository: FeedbackFormsRepository,
                                        categoriesRepository: CategoriesRepository,
                                        dateTimeUtility: DateTimeUtility,
                                        controllerComponents: KnolxControllerComponents,
                                        @Named("SessionsScheduler") sessionsScheduler: ActorRef,
                                        @Named("UsersBanScheduler") usersBanScheduler: ActorRef
                                       ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val subCategoryInformation: OFormat[SubCategoryInformation] = Json.format[SubCategoryInformation]
  implicit val categoryInformation: OFormat[CategoryInformation] = Json.format[CategoryInformation]
  implicit val knolxSessionInformation: OFormat[KnolxSessionInformation] = Json.format[KnolxSessionInformation]
  implicit val filterUserSessionInfoFormat: OFormat[FilterUserSessionInformation] = Json.format[FilterUserSessionInformation]
  implicit val filterSessionInfoFormat: OFormat[KnolxAnalysisDateRange] = Json.format[KnolxAnalysisDateRange]
  implicit val SessionInfoFormat: OFormat[SessionInfo] = Json.format[SessionInfo]
  implicit val knolxMonthlyInfo: OFormat[KnolxMonthlyInfo] = Json.format[KnolxMonthlyInfo]

  val filterSessionsForm = Form(
    mapping(
      "email" -> nonEmptyText,
      "startDateString" -> nonEmptyText,
      "endDateString" -> nonEmptyText
    )(FilterSessions.apply)(FilterSessions.unapply)
  )

  val filterSessionsInformationForm = Form(
    mapping(
      "startDate" -> nonEmptyText,
      "endDate" -> nonEmptyText
    )(KnolxAnalysisDateRange.apply)(KnolxAnalysisDateRange.unapply)
  )


  def renderAnalysisPage: Action[AnyContent] = userAction { implicit request =>
    Ok(views.html.analysis.analysispage())
  }

  def pieChart: Action[AnyContent] = userAction.async { implicit request =>
    filterSessionsInformationForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for filtering sessions " + formWithErrors)
        Future.successful(BadRequest(Json.toJson(List("Akshansh")).toString))
      }, filterSessionsInformationForm => {

        val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm")
        val startDate: Date = dateFormat.parse(filterSessionsInformationForm.startDate)
        val endDate: Date = dateFormat.parse(filterSessionsInformationForm.endDate)

        categoriesRepository.getCategories.flatMap { categoryInfo =>
          val primaryCategoryList = categoryInfo.map(_.categoryName)

          sessionsRepository.sessionsInTimeRange(FilterUserSessionInformation(None, startDate, endDate)).map { sessions =>
            val categoryUsedInSession = sessions.groupBy(_.category).keys.toList
            val categoryNotUsedInSession = primaryCategoryList diff categoryUsedInSession

            val categoriesUsedAnalysisInfo = sessions.groupBy(_.category).map { sessionInfo =>
              val subCategoryInfo: List[SubCategoryInformation] = sessionInfo._2.groupBy(_.subCategory).map(
                sessionBasedSubcategory => SubCategoryInformation(sessionBasedSubcategory._1, sessionBasedSubcategory._2.length)).toList
              CategoryInformation(sessionInfo._1, sessionInfo._2.length, subCategoryInfo)
            }.toList

            val categoriesAnalysisInfo = categoriesUsedAnalysisInfo ::: categoryNotUsedInSession.map(category => CategoryInformation(category, 0, Nil))
            Ok(Json.toJson(KnolxSessionInformation(sessions.length,
              categoriesAnalysisInfo), getAllSubCategoryInfo(sessions),
              getMonthlyInfo(sessions)).toString)
          }
        }
      }
    )
  }

  private def getAllSubCategoryInfo(sessions: List[SessionInfo]): List[SubCategoryInformation] = {
    sessions.groupBy(_.subCategory).map { listOfSubCategory =>
      SubCategoryInformation(listOfSubCategory._1, listOfSubCategory._2.length)
    }.toList
  }

  private def getMonthlyInfo(sessions: List[SessionInfo]): List[KnolxMonthlyInfo] = {
    val monthFormat = new SimpleDateFormat("MMMM")
    val sessionMonthList = sessions.map(session => monthFormat.format(new Date(session.date.value)))

    sessionMonthList.groupBy(identity).map { case (month, monthlySessions) =>
      KnolxMonthlyInfo(month, monthlySessions.length)
    }.toList
  }

}

package controllers

import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// Knolx related analytics classes
case class SubCategoryInformation(subCategoryName: String, totalSessionSubCategory: Int)
case class CategoryInformation(categoryName: String, totalSessionCategory: Int, subCategoryInfo: List[SubCategoryInformation])
case class KnolxSessionInformation(totalSession: Int, categoryInformation: List[CategoryInformation])
case class KnolxMonthlyInfo(monthName: String, total: Int)
case class KnolxAnalysisDateRange(startDate: String, endDate: String)

// User knolx related analytics classes
case class FilterUserSessionInformation(email: Option[String], startDate: Long, endDate: Long)

@Singleton
class KnolxAnalysisController @Inject()(messagesApi: MessagesApi,
                                        sessionsRepository: SessionsRepository,
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
  implicit val knolxMonthlyInfo: OFormat[KnolxMonthlyInfo] = Json.format[KnolxMonthlyInfo]

  implicit val knolxAnalysisDateRangeReads: Reads[KnolxAnalysisDateRange] = (
    (JsPath \ "startDate").read[String] and
      (JsPath \ "endDate").read[String]
    ) (KnolxAnalysisDateRange.apply _)

  def renderAnalysisPage: Action[AnyContent] = action { implicit request =>
    Ok(views.html.analysis.analysispage())
  }

  def renderPieChart: Action[JsValue] = action(parse.json).async { implicit request =>
    request.body.validate[KnolxAnalysisDateRange].fold(
      jsonValidationErrors => {
        Logger.error(s"Received a bad request for Pie Chart" + jsonValidationErrors)
        Future.successful(BadRequest(JsError.toJson(jsonValidationErrors)))
      }, knolxAnalysisDateRange => {
        val startDate: Long = dateTimeUtility.parseDateString(knolxAnalysisDateRange.startDate)
        val endDate: Long = dateTimeUtility.parseDateString(knolxAnalysisDateRange.endDate)

        categoriesRepository.getCategories.flatMap { categoryInfo =>
          val primaryCategoryList = categoryInfo.map(_.categoryName)
          sessionsRepository.sessionsInTimeRange(FilterUserSessionInformation(None, startDate, endDate)).map { sessions =>
            val categoryUsedInSession = sessions.groupBy(_.category).keys.toList
            val categoryNotUsedInSession = primaryCategoryList diff categoryUsedInSession

            val categoriesUsedAnalysisInfo = sessions.groupBy(_.category).map { case (categoryName, categoryBasedSession) =>
              val subCategoryInfo: List[SubCategoryInformation] = categoryBasedSession.groupBy(_.subCategory).map {
                case (subCategoryName, sessionBasedSubcategory) => SubCategoryInformation(subCategoryName, sessionBasedSubcategory.length)
              }.toList
              CategoryInformation(categoryName, categoryBasedSession.length, subCategoryInfo)
            }.toList

            val categoriesAnalysisInfo = categoriesUsedAnalysisInfo ::: categoryNotUsedInSession.map(category => CategoryInformation(category, 0, Nil))
            Ok(Json.toJson(KnolxSessionInformation(sessions.length,
              categoriesAnalysisInfo)))
          }
        }
      }
    )
  }

  def renderColumnChart: Action[JsValue] = action(parse.json).async { implicit request =>
    request.body.validate[KnolxAnalysisDateRange].fold(
      jsonValidationErrors => {
        Logger.error(s"Received a bad request for filtering sessions " + jsonValidationErrors)
        Future.successful(BadRequest(JsError.toJson(jsonValidationErrors)))
      }, knolxAnalysisDateRange => {
        val startDate: Long = dateTimeUtility.parseDateString(knolxAnalysisDateRange.startDate)
        val endDate: Long = dateTimeUtility.parseDateString(knolxAnalysisDateRange.endDate)

        sessionsRepository.sessionsInTimeRange(FilterUserSessionInformation(None, startDate, endDate)).map { sessions =>
          val subCategoryList = sessions.groupBy(_.subCategory).map { case (subCategory, session) =>
            SubCategoryInformation(subCategory, session.length)
          }.toList
          Ok(Json.toJson(subCategoryList))
        }
      })
  }

  def renderLineChart: Action[JsValue] = action(parse.json).async { implicit request =>
    request.body.validate[KnolxAnalysisDateRange].fold(
      jsonValidationErrors => {
        Logger.error(s"Received a bad request for filtering sessions " + jsonValidationErrors)
        Future.successful(BadRequest(JsError.toJson(jsonValidationErrors)))
      }, knolxAnalysisDateRange => {
        val startDate: Long = dateTimeUtility.parseDateString(knolxAnalysisDateRange.startDate)
        val endDate: Long = dateTimeUtility.parseDateString(knolxAnalysisDateRange.endDate)

        sessionsRepository.sessionsInTimeRange(FilterUserSessionInformation(None, startDate, endDate)).map { sessions =>
          val sessionMonthList = sessions.map(session => dateTimeUtility.getMonth(session.date.value))
          val sessionMonthInfo = sessionMonthList.groupBy(identity).map { case (month, monthlySessions) =>
            KnolxMonthlyInfo(month, monthlySessions.length)
          }.toList
          Ok(Json.toJson(sessionMonthInfo))
        }
      })
  }

}

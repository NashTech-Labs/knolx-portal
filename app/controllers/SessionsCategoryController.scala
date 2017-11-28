package controllers

import javax.inject.{Inject, Singleton}
import models._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ModelsCategoryInformation(_id: String, categoryName: String, subCategory: List[String])

@Singleton
class SessionsCategoryController @Inject()(messagesApi: MessagesApi,
                                           usersRepository: UsersRepository,
                                           sessionsRepository: SessionsRepository,
                                           categoriesRepository: CategoriesRepository,
                                           dateTimeUtility: DateTimeUtility,
                                           controllerComponents: KnolxControllerComponents
                                          ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val modelsCategoriesFormat: OFormat[ModelsCategoryInformation] = Json.format[ModelsCategoryInformation]

  def renderCategoryPage: Action[AnyContent] = adminAction.async { implicit request =>
    categoriesRepository
      .getCategories
      .map(category => Ok(views.html.category(category)))
  }

  def addPrimaryCategory(categoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    val cleanedCategoryName = categoryName.trim

    if (cleanedCategoryName.isEmpty) {
      Future.successful(BadRequest("Primary category cannot be empty"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>
          if (categories.exists(_.categoryName.toLowerCase == cleanedCategoryName.toLowerCase)) {
            Future.successful(BadRequest("Primary category already exists"))
          } else {
            categoriesRepository
              .insertCategory(cleanedCategoryName)
              .map { result =>
                if (result.ok) {
                  Logger.info(s"Primary category was successfully added $categoryName")
                  Ok("Primary category was successfully added")
                } else {
                  Logger.error(s"Something went wrong while adding primary category $categoryName")
                  BadRequest("Primary category cannot be added due to some error")
                }
              }
          }
        }
    }
  }

  def addSubCategory(categoryName: String, subCategory: String): Action[AnyContent] = adminAction.async { implicit request =>
    val cleanedSubCategoryName = subCategory.trim

    if (cleanedSubCategoryName.isEmpty) {
      Future.successful(BadRequest("Subcategory cannot be empty"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>
          categories.find(_.categoryName == categoryName)
            .fold {
              Logger.info(s"No primary category found for category name $categoryName")
              Future.successful(BadRequest("No primary category found."))
            } { categoryInfo =>
              val newSubCategory = categoryInfo.subCategory.find(_.toLowerCase == cleanedSubCategoryName.toLowerCase)
              newSubCategory.fold {
                val subCategoryInfo = CategoryInfo(categoryName, List(cleanedSubCategoryName), categoryInfo._id)
                categoriesRepository
                  .upsert(subCategoryInfo)
                  .map { result =>
                    if (result.ok) {
                      Logger.info(s"Sub-category was successfully added $subCategory")
                      Ok("Sub-category was successfully added")
                    } else {
                      Logger.error(s"Something went wrong while adding sub-category $subCategory")
                      BadRequest("Unsuccessful sub-category added")
                    }
                  }
              } { _ =>
                Future.successful(BadRequest("Error! Sub-category already exists"))
              }
            }
        }
    }
  }

  def modifyPrimaryCategory(categoryId: String, newCategoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    val cleanedCategoryName = newCategoryName.trim

    if (cleanedCategoryName.isEmpty) {
      Future.successful(BadRequest("Modify primary category cannot be empty"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>
          val category = categories.filter(_._id.stringify == categoryId).head
          sessionsRepository
            .updateCategoryOnChange(category.categoryName, cleanedCategoryName)
            .flatMap { session =>
              if (session.ok) {
                categoriesRepository
                  .modifyPrimaryCategory(category._id.stringify, cleanedCategoryName)
                  .map { result =>
                    if (result.ok) {
                      Logger.info(s"Primary category was successfully modified $newCategoryName")
                      Ok("Primary category was successfully modified")
                    } else {
                      Logger.error(s"Something went wrong while modifying primary category $newCategoryName")
                      BadRequest("Unsuccessful attempt to modify primary category")
                    }
                  }
              } else {
                Future.successful(BadRequest("Update on session table was unsuccessful"))
              }
            }
        }
    }
  }

  def modifySubCategory(categoryName: String,
                        oldSubCategoryName: String,
                        newSubCategoryName: String): Action[AnyContent] = adminAction.async { implicit request =>
    val cleanSubCategory = newSubCategoryName.trim

    if (cleanSubCategory.isEmpty) {
      Future.successful(BadRequest("Modify sub-category cannot be empty"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>
          val subCategoryList =
            categories.filter(_.categoryName.toLowerCase == categoryName.toLowerCase).flatMap(_.subCategory.map(_.toLowerCase))
          val subCategoryExists = subCategoryList.contains(cleanSubCategory.toLowerCase)

          if (subCategoryExists) {
            Future.successful(BadRequest("Sub-category already exists"))
          } else {
            sessionsRepository
              .updateSubCategoryOnChange(oldSubCategoryName, newSubCategoryName)
              .flatMap { session =>
                if (session.ok) {
                  categoriesRepository
                    .modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName)
                    .map {
                      result =>
                        if (result.ok) {
                          Logger.info(s"Sub-category was successfully modified $newSubCategoryName")
                          Ok("Successfully Modified sub category")
                        } else {
                          Logger.error(s"Something went wrong while modifying sub-category $newSubCategoryName")
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
    if (categoryId.trim().isEmpty) {
      Future.successful(BadRequest("Please select a valid primary category"))
    } else {
      categoriesRepository.getCategories.flatMap {
        categories =>
          val category = categories.find {
            c =>
              c._id.toString() == categoryId
          }
          category.fold {
            Future.successful(BadRequest("No such primary category exists"))
          } { category =>
            val subCategoryList = category.subCategory
            if (subCategoryList.isEmpty) {
              sessionsRepository.updateCategoryOnChange(category.categoryName, "").flatMap { session =>
                if (session.ok) {
                  categoriesRepository.deletePrimaryCategory(category._id.stringify).map { result =>
                    if (result.ok) {
                      Logger.info(s"Primary category with categoryId $categoryId was successfully deleted")
                      Ok("Primary category was successfully deleted")
                    } else {
                      Logger.error(s"Something went wrong while deleting primary category with category Id $categoryId")
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
          if (subCategoryList.isEmpty) {
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
      sessionsRepository.updateSubCategoryOnChange(subCategory, "").flatMap { sessions =>
        if (sessions.ok) {
          categoriesRepository.deleteSubCategory(categoryName, subCategory).flatMap { result =>
            if (result.ok) {
              Logger.info(s"Sub-category was successfully deleted $subCategory")
              Future.successful(Ok("Sub-category was successfully deleted"))
            } else {
              Logger.error(s"Something went wrong while deleting $subCategory")
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
      sessionsRepository.sessions.map { sessionInformation =>
        val sessionTopicList = sessionInformation.filter {
          session => session.subCategory == subCategory && session.category == categoryName
        }.map {
          _.topic
        }
        Ok(Json.toJson(sessionTopicList).toString())
      }
    }
  }

  def getCategory: Action[AnyContent] = action.async { implicit request =>
    categoriesRepository.getCategories.map { categories =>
      val listOfCategoryInfo = categories.map(category => ModelsCategoryInformation(category._id.stringify, category.categoryName, category.subCategory))
      Ok(Json.toJson(listOfCategoryInfo).toString)
    }
  }
}

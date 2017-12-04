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

case class ModelsCategoryInformation(categoryId: String, categoryName: String, subCategory: List[String])

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
      Future.successful(Ok(views.html.sessions.category()))
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
      Future.successful(BadRequest("Sub-category cannot be empty"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>
          categories.find(_.categoryName == categoryName)
            .fold {
              Logger.info(s"No primary category found for category name $categoryName")
              Future.successful(BadRequest("No primary category found"))
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
                      BadRequest("Sub-category cannot be added due to some error")
                    }
                  }
              } { _ =>
                Future.successful(BadRequest("Sub-category already exists"))
              }
            }
        }
    }
  }

  def modifyPrimaryCategory(categoryId: String, newCategoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    val cleanedCategoryName = newCategoryName.trim

    if (cleanedCategoryName.isEmpty) {
      Future.successful(BadRequest("Primary category cannot be empty"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>

          val maybeCategory = categories.find(_._id.stringify == categoryId)
          maybeCategory.fold {
            Future.successful(BadRequest("No primary category found"))
          } { category =>
            categoriesRepository
              .modifyPrimaryCategory(category._id.stringify, cleanedCategoryName)
              .flatMap { result =>
                if (result.ok) {
                  sessionsRepository
                    .updateCategoryOnChange(category.categoryName, cleanedCategoryName)
                    .map { session =>
                      if (session.ok) {
                        Logger.info(s"Primary category was successfully modified $newCategoryName")
                        Ok("Primary category was successfully modified")
                      } else {
                        Logger.error(s"Something went wrong while modifying primary category $newCategoryName")
                        BadRequest("Primary category cannot be modified due to some error")
                      }
                    }
                } else {
                  Future.successful(BadRequest("Something went wrong when updating primary category"))
                }
              }
          }
        }
    }
  }

  def modifySubCategory(categoryId: String,
                        oldSubCategoryName: String,
                        newSubCategoryName: String): Action[AnyContent] = adminAction.async { implicit request =>
    val cleanedSubCategory = newSubCategoryName.trim

    if (cleanedSubCategory.isEmpty) {
      Future.successful(BadRequest("Sub-category cannot be empty"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>
          val subCategoryList =
            categories.filter(_._id.stringify == categoryId).flatMap(_.subCategory.map(_.toLowerCase))
          val subCategoryExists = subCategoryList.contains(oldSubCategoryName.toLowerCase)

          val newSubCategoryExists = subCategoryList
            .filterNot(_ == oldSubCategoryName.toLowerCase)
            .contains(newSubCategoryName.toLowerCase)
          if (!subCategoryExists) {
            Future.successful(BadRequest("No sub-category found"))
          } else if(newSubCategoryExists) {
            Future.successful(BadRequest("Sub-category already exists"))
          } else {
            categoriesRepository
              .modifySubCategory(categoryId, oldSubCategoryName, cleanedSubCategory)
              .flatMap { result =>
                  if (result.ok) {
                    sessionsRepository
                      .updateSubCategoryOnChange(oldSubCategoryName, newSubCategoryName)
                      .map { session =>
                        if (session.ok) {
                          Logger.info(s"Sub-category was successfully modified $newSubCategoryName")
                          Ok("Sub-category was successfully modified")
                        } else {
                          Logger.error(s"Something went wrong while modifying sub-category $newSubCategoryName")
                          BadRequest("Sub-category cannot be modified due to some error")
                        }
                      }
                  }
                  else {
                    Future.successful(BadRequest("Something went wrong when updating sub-category"))
                  }
              }
          }
        }
    }
  }

  def deletePrimaryCategory(categoryId: String): Action[AnyContent] = superUserAction.async { implicit request =>
    if (categoryId.isEmpty) {
      Future.successful(BadRequest("Please select a valid primary category"))
    } else {
      categoriesRepository
        .getCategories
        .flatMap { categories =>
          val category = categories.find(_._id.stringify == categoryId)

          category.fold {
            Future.successful(BadRequest("No primary category found"))
          } { category =>
            if (category.subCategory.isEmpty) {
              categoriesRepository
                .deletePrimaryCategory(category._id.stringify)
                .flatMap { result =>
                  if (result.ok) {
                    sessionsRepository
                      .updateCategoryOnChange(category.categoryName, "")
                      .map { session =>
                        if (session.ok) {
                          Logger.info(s"Primary category with categoryId $categoryId was successfully deleted")
                          Ok("Primary category was successfully deleted")
                        } else {
                          Logger.error(s"Something went wrong while deleting primary category with category Id $categoryId")
                          BadRequest("Primary category cannot be deleted due to some error")
                        }
                      }
                  } else {
                    Future.successful(BadRequest("Something went wrong when deleting primary category"))
                  }
                }
            } else {
              Future.successful(BadRequest("All sub categories should be deleted prior to deleting the primary category"))
            }
          }
        }
    }
  }

  def getSubCategoryByPrimaryCategory(categoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    val cleanedCategoryName = categoryName.trim

    if (cleanedCategoryName.isEmpty) {
      Future.successful(BadRequest("Please select a valid primary category"))
    } else {
      categoriesRepository
        .getCategories
        .map { categories =>
          val subCategoryList =
            categories.filter(category => category.categoryName.toLowerCase == cleanedCategoryName.toLowerCase).flatMap(_.subCategory)
          Ok(Json.toJson(subCategoryList))
        }
    }
  }

  def deleteSubCategory(categoryId: String, subCategory: String): Action[AnyContent] = adminAction.async { implicit request =>
    val cleanedSubCategory = subCategory.trim

    if (cleanedSubCategory.isEmpty) {
      Future.successful(BadRequest("Sub-category cannot be empty"))
    } else {
      categoriesRepository
        .deleteSubCategory(categoryId, cleanedSubCategory)
        .flatMap { result =>
          if (result.ok) {
            categoriesRepository
              .getCategoryNameById(categoryId).flatMap {
              _.fold {
                Future.successful(BadRequest("No primary category found"))
              } { categoryName =>
                sessionsRepository
                  .updateSubCategoryOnChange(categoryName, "")
                  .map { session =>
                    if (session.ok) {
                      Logger.info(s"Sub-category was successfully deleted $subCategory")
                      Ok("Sub-category was successfully deleted")
                    } else {
                      Logger.error(s"Something went wrong while deleting $subCategory")
                      BadRequest("Sub-category cannot be deleted due to some error")
                    }
                  }
              }
            }
          } else {
            Future.successful(BadRequest("Something went wrong while deleting sub-category"))
          }
        }
    }
  }

  def getTopicsBySubCategory(categoryName: String, subCategory: String): Action[AnyContent] = adminAction.async { implicit request =>
    if (subCategory.trim.isEmpty) {
      Future.successful(BadRequest("Please select a valid sub-category"))
    } else {
      sessionsRepository
        .getSessionByCategory(categoryName, subCategory)
        .map { sessionInformation =>
          val sessionTopicList =
            sessionInformation.map(_.topic)
          Ok(Json.toJson(sessionTopicList))
        }
    }
  }

  def getCategory: Action[AnyContent] = action.async { implicit request =>
    categoriesRepository
      .getCategories
      .map { categories =>
        val listOfCategoryInfo = categories.map(category => ModelsCategoryInformation(category._id.stringify,category.categoryName, category.subCategory))
        Ok(Json.toJson(listOfCategoryInfo))
      }
  }
  
}

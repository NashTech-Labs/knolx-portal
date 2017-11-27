package controllers

import javax.inject.{Inject, Singleton}

import models._
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
    categoriesRepository.getCategories.map {
      category =>
        Ok(views.html.category(category))
    }
  }

  def addPrimaryCategory(categoryName: String): Action[AnyContent] = superUserAction.async { implicit request =>
    if (categoryName.trim().isEmpty) {
      Future.successful(BadRequest("Primary category cannot be empty"))
    } else {
      categoriesRepository.getCategories.flatMap { result =>
        if (result.exists(category => category.categoryName.toLowerCase.equals(categoryName.toLowerCase))) {
          Future.successful(BadRequest("Primary category already exists"))
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
              categoriesRepository.upsert(subCategoryInfo).map { result =>
                if (result.ok) {
                  Ok("Sub-category was successfully added")
                } else {
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
    if (newCategoryName.trim().isEmpty) {
      Future.successful(BadRequest("Modify primary category cannot be empty"))
    } else {
      categoriesRepository.getCategories.flatMap { result =>
        val category = result.filter(c => c._id.stringify == categoryId).head
        sessionsRepository.updateCategoryOnChange(category.categoryName, newCategoryName).flatMap { session =>
          if (session.ok) {
            categoriesRepository.modifyPrimaryCategory(category._id.stringify, newCategoryName).map { result =>
              if (result.ok) {
                Ok("Primary category was successfully modified")
              } else {
                BadRequest("Unsuccessfully attempt to modify primary category")
              }
            }
          } else {
            Future.successful(BadRequest("Update on session table was unsuccessful"))
          }
        }
      }
    }
  }

  def modifySubCategory(categoryName: String, oldSubCategoryName: String,
                        newSubCategoryName: String): Action[AnyContent] = adminAction.async { implicit request =>
    if (newSubCategoryName.trim().isEmpty) {
      Future.successful(BadRequest("Modify sub-category cannot be empty"))
    } else {
      categoriesRepository.getCategories.flatMap {
        categories =>
          val subCategoryList = categories.filter {
            category => category.categoryName.toLowerCase == categoryName.toLowerCase
          }.flatMap(_.subCategory)
          val check = subCategoryList.contains(newSubCategoryName)
          if (check) {
            Future.successful(BadRequest("Sub-category already exists"))
          } else {
            sessionsRepository.updateSubCategoryOnChange(oldSubCategoryName, newSubCategoryName).flatMap { session =>
              if (session.ok) {
                categoriesRepository.modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName).map {
                  result =>
                    if (result.ok) {
                      Ok("Successfully Modified sub category")
                    } else {
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

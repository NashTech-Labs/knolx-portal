package controllers

import javax.inject.Inject

import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter

class JavascriptRouter @Inject()(controllerComponents: KnolxControllerComponents) extends KnolxAbstractController(controllerComponents) {

  def jsRoutes: Action[AnyContent] = action { implicit request =>
    Ok(JavaScriptReverseRouter("jsRoutes")(
      controllers.routes.javascript.FeedbackFormsController.createFeedbackForm,
      controllers.routes.javascript.FeedbackFormsController.manageFeedbackForm,
      controllers.routes.javascript.FeedbackFormsController.updateFeedbackForm,
      controllers.routes.javascript.FeedbackFormsController.getFeedbackFormPreview,
      controllers.routes.javascript.UsersController.searchUser,
      controllers.routes.javascript.UsersController.getByEmail,
      controllers.routes.javascript.UsersController.deleteUser,
      controllers.routes.javascript.SessionsController.searchManageSession,
      controllers.routes.javascript.SessionsController.searchSessions,
      controllers.routes.javascript.SessionsController.shareContent,
      controllers.routes.javascript.SessionsController.update,
      controllers.routes.javascript.SessionsController.deleteSession,
      controllers.routes.javascript.FeedbackFormsResponseController.storeFeedbackFormResponse,
      controllers.routes.javascript.FeedbackFormsResponseController.fetchFeedbackFormResponse,
      controllers.routes.javascript.FeedbackFormsResponseController.getFeedbackFormsForToday,
      controllers.routes.javascript.FeedbackFormsReportController.searchAllResponsesBySessionId,
      controllers.routes.javascript.SessionsCategoryController.addPrimaryCategory,
      controllers.routes.javascript.SessionsCategoryController.addSubCategory,
      controllers.routes.javascript.SessionsCategoryController.modifyPrimaryCategory,
      controllers.routes.javascript.SessionsCategoryController.modifySubCategory,
      controllers.routes.javascript.SessionsCategoryController.getCategory,
      controllers.routes.javascript.SessionsCategoryController.deletePrimaryCategory,
      controllers.routes.javascript.SessionsCategoryController.getSubCategoryByPrimaryCategory,
      controllers.routes.javascript.SessionsCategoryController.deleteSubCategory,
      controllers.routes.javascript.SessionsCategoryController.getTopicsBySubCategory,
      controllers.routes.javascript.KnolxAnalysisController.renderColumnChart,
      controllers.routes.javascript.KnolxAnalysisController.renderPieChart,
      controllers.routes.javascript.KnolxAnalysisController.renderLineChart,
      controllers.routes.javascript.KnolxAnalysisController.leaderBoard,
      controllers.routes.javascript.FeedbackFormsReportController.manageAllFeedbackReports,
      controllers.routes.javascript.FeedbackFormsReportController.manageUserFeedbackReports,
      controllers.routes.javascript.FeedbackFormsReportController.fetchAllResponsesBySessionId,
      controllers.routes.javascript.FeedbackFormsReportController.fetchUserResponsesBySessionId
    )).as("text/javascript")
  }

}

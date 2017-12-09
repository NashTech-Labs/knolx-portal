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
      controllers.routes.javascript.SessionsController.getCategory,
      controllers.routes.javascript.KnolxAnalysisController.renderColumnChart,
      controllers.routes.javascript.KnolxAnalysisController.renderPieChart,
      controllers.routes.javascript.KnolxAnalysisController.renderLineChart,
      controllers.routes.javascript.FeedbackFormsReportController.manageAllFeedbackReports,
      controllers.routes.javascript.FeedbackFormsReportController.manageUserFeedbackReports,
      controllers.routes.javascript.FeedbackFormsReportController.fetchAllResponsesBySessionId,
      controllers.routes.javascript.FeedbackFormsReportController.fetchUserResponsesBySessionId,
      controllers.routes.javascript.UsersController.usersList,
      controllers.routes.javascript.KnolxUserAnalysisController.userSessionsResponseComparison,
      controllers.routes.javascript.KnolxUserAnalysisController.getBanCount,
      controllers.routes.javascript.KnolxUserAnalysisController.getUserDidNotAttendSessionCount,
      controllers.routes.javascript.KnolxUserAnalysisController.getUserTotalKnolx,
      controllers.routes.javascript.KnolxUserAnalysisController.getUserTotalMeetUps
    )).as("text/javascript")
  }

}

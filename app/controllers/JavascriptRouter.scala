package controllers

import play.api.mvc.{AnyContent, Action, Controller}
import play.api.routing.JavaScriptReverseRouter

class JavascriptRouter extends Controller {

  def jsRoutes: Action[AnyContent] = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        controllers.routes.javascript.FeedbackFormsController.createFeedbackForm
      )
    ).as("text/javascript")
  }

}

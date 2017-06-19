package controllers

import javax.inject._

import play.api.mvc._

@Singleton
class HomeController @Inject() extends Controller {

  def index: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.SessionsController.sessions(1))
  }

}

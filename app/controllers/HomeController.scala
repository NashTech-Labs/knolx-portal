package controllers

import javax.inject._

import play.api.mvc._

@Singleton
class HomeController @Inject()(controllerComponents: KnolxControllerComponents) extends AbstractController(controllerComponents) {

  def index: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.SessionsController.sessions(1))
  }

}

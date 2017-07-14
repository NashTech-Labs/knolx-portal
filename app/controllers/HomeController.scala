package controllers

import javax.inject._

import play.api.mvc._

@Singleton
class HomeController @Inject()(controllerComponents: KnolxControllerComponents) extends KnolxAbstractController(controllerComponents) {

  def index: Action[AnyContent] = action { implicit request =>
    Redirect(routes.SessionsController.sessions(1))
  }

}

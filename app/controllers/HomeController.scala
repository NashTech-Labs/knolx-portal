package controllers

import javax.inject._

import play.api.Logger
import play.api.mvc._

@Singleton
class HomeController @Inject()(controllerComponents: KnolxControllerComponents) extends KnolxAbstractController(controllerComponents) {

  def index: Action[AnyContent] = action { implicit request =>
    Logger.info("---------------------It's coming here too bro")
    Redirect(routes.SessionsController.sessions(1, None))
  }

}

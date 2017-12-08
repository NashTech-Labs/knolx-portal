package controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}

@Singleton
class RecommendationController  @Inject()(messagesApi: MessagesApi,
                                          controllerComponents: KnolxControllerComponents
                                         ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  def renderRecommendationPage: Action[AnyContent] = action { implicit request =>
    Ok(views.html.recommendations.recommendation())
  }
}

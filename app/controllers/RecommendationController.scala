package controllers

import javax.inject.{Inject, Singleton}

import controllers.EmailHelper.isValidEmail
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsPath, Writes}
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


case class RecommendationForm(email: String, recommendation: String)

@Singleton
class RecommendationController @Inject()(messagesApi: MessagesApi,
                                         controllerComponents: KnolxControllerComponents
                                        ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  def renderRecommendationPage: Action[AnyContent] = action { implicit request =>
  Ok(views.html.recommendations.recommendation())
  }

  implicit val recommendationFormReads: Format[RecommendationForm] = (
    (JsPath \ "email").format[String](verifying[String](isValidEmail)) and
      (JsPath \ "recommendation").format[String](minLength[String](10) keepAnd maxLength[String](150))
    ) (RecommendationForm.apply, unlift(RecommendationForm.unapply))

  def addRecommendation(): Action[JsValue] = userAction(parse.json).async { implicit request =>
  request.body.validate[RecommendationForm].fold(
    jsonValidationErrors => {
      Logger.error(s"Received a bad request during adding recommendation " + jsonValidationErrors)
      Future.successful(BadRequest(JsError.toJson(jsonValidationErrors)))
    }, _ => {
      Future.successful(Ok(Json.toJson("ghj")))
    }
  )
  }
}

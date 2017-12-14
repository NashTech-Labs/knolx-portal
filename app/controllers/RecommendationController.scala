package controllers

import javax.inject.{Inject, Singleton}
import EmailHelper.isValidEmail

import models.{RecommendationInfo, RecommendationsRepository}
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.JsPath
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class RecommendationForm(email: String, recommendation: String)

case class Recommendation(email: String,
                          recommendation: String,
                          approved: Boolean,
                          id: String)

@Singleton
class RecommendationController @Inject()(messagesApi: MessagesApi,
                                         recommendationsRepository: RecommendationsRepository,
                                         controllerComponents: KnolxControllerComponents
                                        ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val recommendationFormReads: Format[RecommendationForm] = (
    (JsPath \ "email").format[String](verifying[String](isValidEmail)) and
      (JsPath \ "recommendation").format[String](minLength[String](10) keepAnd maxLength[String](10))
    ) (RecommendationForm.apply, unlift(RecommendationForm.unapply))
  implicit val recommendationsFormat: OFormat[Recommendation] = Json.format[Recommendation]

  def renderRecommendationPage: Action[AnyContent] = userAction { implicit request =>
    Ok(views.html.recommendations.recommendation())
  }

  def addRecommendation(): Action[JsValue] = userAction(parse.json).async { implicit request =>
    request.body.validate[RecommendationForm].fold(
      jsonValidationErrors => {
        Logger.error(s"Received a bad request during adding recommendation " + jsonValidationErrors)
        Future.successful(BadRequest(JsError.toJson(jsonValidationErrors)))
      },
      recommendationForm => {
        if (recommendationForm.email.equals(request.user.email)) {
          val recommendationInfo = RecommendationInfo(request.user.email, recommendationForm.recommendation)

          recommendationsRepository.insert(recommendationInfo).map { result =>
            if (result.ok) {
              Ok(Json.toJson("Your Recommendation has been successfully received"))
            } else {
              BadRequest(Json.toJson("Get Internal Server Error During Insertion"))
            }
          }
        } else {
          Future.successful(BadRequest(Json.toJson("Invalid Email")))
        }
      }
    )
  }

  def recommendationList: Action[AnyContent] = userAction.async { implicit request =>
    recommendationsRepository.getAllRecommendations map { recommendations =>
      val recommendationList = recommendations map { recommendation =>
        Recommendation(recommendation.email, recommendation.recommendation, recommendation.approved, recommendation._id.stringify)

      }
      Ok(Json.toJson(recommendationList))
    }
  }

  def approveRecommendation(recommendationId: String): Action[AnyContent] = adminAction.async { implicit request =>
    recommendationsRepository.approveRecommendation(recommendationId).map { result =>
      if (result.ok) {
        Ok(Json.toJson("Recommendation Successfully Approved"))
      } else {
        BadRequest(Json.toJson("Get Internal Server Error During Approval"))
      }
    }
  }

  def declineRecommendation(recommendationId: String): Action[AnyContent] = adminAction.async { implicit request =>
    recommendationsRepository.declineRecommendation(recommendationId).map { result =>
      if (result.ok) {
        Ok(Json.toJson("Recommendation Successfully Approved"))
      } else {
        BadRequest(Json.toJson("Get Internal Server Error During Approval"))
      }
    }
  }

  def userRecommendation: Action[AnyContent] = userAction.async { implicit request =>
    recommendationsRepository.getUserRecommendation(request.user.email).map { recommendations =>
      Ok(Json.toJson(recommendations))
    }
  }

}

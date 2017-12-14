package controllers

import java.time.LocalDate
import java.util.Date
import javax.inject.{Inject, Singleton}

import EmailHelper.isValidEmail
import models.{RecommendationInfo, RecommendationResponseRepository, RecommendationResponseRepositoryInfo, RecommendationsRepository}
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.JsPath
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class Recommendation(email: String,
                          recommendation: String,
                          submissionDate: LocalDate,
                          updateDate: LocalDate,
                          approved: Boolean,
                          decline: Boolean,
                          pending: Boolean,
                          done: Boolean,
                          upVotes: Int,
                          downVotes: Int,
                          id: String)

@Singleton
class RecommendationController @Inject()(messagesApi: MessagesApi,
                                         recommendationsRepository: RecommendationsRepository,
                                         controllerComponents: KnolxControllerComponents,
                                         dateTimeUtility: DateTimeUtility,
                                         recommendationResponseRepository: RecommendationResponseRepository
                                        ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  /*implicit val recommendationFormReads: Format[RecommendationForm] = (
    (JsPath \ "email").format[String](verifying[String](isValidEmail)) and
      (JsPath \ "recommendation").format[String](minLength[String](10) keepAnd maxLength[String](10))
    ) (RecommendationForm.apply, unlift(RecommendationForm.unapply))*/
  implicit val recommendationsFormat: OFormat[Recommendation] = Json.format[Recommendation]

  def renderRecommendationPage: Action[AnyContent] = action { implicit request =>
    Ok(views.html.recommendations.recommendation())
  }

  def addRecommendation(recommendation: String): Action[AnyContent] = action.async { implicit request =>
    val email = if (SessionHelper.email.nonEmpty) Some(SessionHelper.email) else None
    val recommendationInfo = RecommendationInfo(email,
      recommendation,
      BSONDateTime(dateTimeUtility.nowMillis),
      BSONDateTime(dateTimeUtility.nowMillis))

    recommendationsRepository.insert(recommendationInfo).map { result =>
      if (result.ok) {
        Ok(Json.toJson("Your Recommendation has been successfully received"))
      } else {
        BadRequest(Json.toJson("Get Internal Server Error During Insertion"))
      }
    }
  }

  def recommendationList(pageNumber: Int, filter: String = "all"): Action[AnyContent] = action.async { implicit request =>

    recommendationsRepository.paginate(pageNumber, filter) map { recommendations =>
      val recommendationList = recommendations map { recommendation =>
        val email = recommendation.email.fold("Anonymous")(identity)
        Recommendation(email,
          recommendation.recommendation,
          dateTimeUtility.toLocalDate(recommendation.submissionDate.value),
          dateTimeUtility.toLocalDate(recommendation.updateDate.value),
          recommendation.approved,
          recommendation.decline,
          recommendation.pending,
          recommendation.done,
          recommendation.upVotes,
          recommendation.downVotes,
          recommendation._id.stringify)
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

  def downVote(email: String, recommendationId: String): Action[AnyContent] = userAction.async { implicit request =>
    Logger.info(s"Downvoting recommendation => $recommendationId")
    recommendationResponseRepository.getVote(email, recommendationId) map { vote =>
      val recommendationResponse = RecommendationResponseRepositoryInfo(email,
        recommendationId,
        upVote = false,
        downVote = true)
      if (vote.equals("upvote")) {
        recommendationsRepository.downVote(recommendationId, alreadyVoted = true)
      } else {
        recommendationsRepository.downVote(recommendationId, alreadyVoted = false)
      }
      recommendationResponseRepository.upsert(recommendationResponse)
      Ok("Downvoted")
    }
  }

  def upVote(email: String, recommendationId: String): Action[AnyContent] = userAction.async { implicit request =>
    Logger.info(s"Upvoting recommendation => $recommendationId")
    recommendationResponseRepository.getVote(email, recommendationId) map { vote =>
      val recommendationResponse = RecommendationResponseRepositoryInfo(email,
        recommendationId,
        upVote = true,
        downVote = false)
      if (vote.equals("downvote")) {
        recommendationsRepository.upVote(recommendationId, alreadyVoted = true)
      } else {
        recommendationsRepository.upVote(recommendationId, alreadyVoted = false)
      }
      recommendationResponseRepository.upsert(recommendationResponse)
      Ok("Upvoted")
    }
  }

}

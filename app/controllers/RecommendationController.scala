package controllers

import java.time.{LocalDate, LocalDateTime}
import javax.inject.{Inject, Singleton}

import models.{RecommendationInfo, RecommendationResponseRepository, RecommendationResponseRepositoryInfo, RecommendationsRepository}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Recommendation(email: Option[String],
                          recommendation: String,
                          submissionDate: Option[LocalDateTime],
                          updateDate: Option[LocalDateTime],
                          approved: Option[Boolean],
                          decline: Option[Boolean],
                          pending: Boolean,
                          done: Boolean,
                          isLoggedIn: Boolean,
                          votes: Int,
                          upVote: Boolean,
                          downVote: Boolean,
                          id: String)

@Singleton
class RecommendationController @Inject()(messagesApi: MessagesApi,
                                         recommendationsRepository: RecommendationsRepository,
                                         controllerComponents: KnolxControllerComponents,
                                         dateTimeUtility: DateTimeUtility,
                                         recommendationResponseRepository: RecommendationResponseRepository
                                        ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

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
        Ok(Json.toJson("Your Recommendation has been successfully received. Wait for approval."))
      } else {
        BadRequest(Json.toJson("Get Internal Server Error During Insertion"))
      }
    }
  }

  def recommendationList(pageNumber: Int, filter: String = "all", sortBy: String): Action[AnyContent] = action.async { implicit request =>
    recommendationsRepository.paginate(pageNumber, filter, sortBy) flatMap { recommendations =>
      if (SessionHelper.isSuperUser || SessionHelper.isAdmin) {
        Future.sequence(recommendations map { recommendation =>
          recommendationResponseRepository.getVote(SessionHelper.email, recommendation._id.stringify) map { recommendationVote =>
            val email = recommendation.email.fold("Anonymous")(identity)
            Recommendation(Some(email),
              recommendation.recommendation,
              Some(dateTimeUtility.toLocalDateTime(recommendation.submissionDate.value)),
              Some(dateTimeUtility.toLocalDateTime(recommendation.updateDate.value)),
              Some(recommendation.approved),
              Some(recommendation.decline),
              recommendation.pending,
              recommendation.done,
              isLoggedIn = true,
              recommendation.upVotes - recommendation.downVotes,
              upVote = if (recommendationVote.equals("upvote")) true else false,
              downVote = if (recommendationVote.equals("downvote")) true else false,
              recommendation._id.stringify)
          }
        }) map { recommendationList => Ok(Json.toJson(recommendationList)) }
      } else {
        Future.sequence(recommendations filter (_.approved) map { recommendation =>
          recommendationResponseRepository.getVote(SessionHelper.email, recommendation._id.stringify) map { recommendationVote =>
            Recommendation(None,
              recommendation.recommendation,
              None,
              None,
              None,
              None,
              recommendation.pending,
              recommendation.done,
              isLoggedIn = !SessionHelper.isLoggedIn,
              recommendation.upVotes - recommendation.downVotes,
              upVote = if (recommendationVote.equals("upvote") && !SessionHelper.isLoggedIn) true else false,
              downVote = if (recommendationVote.equals("downvote") && !SessionHelper.isLoggedIn) true else false,
              recommendation._id.stringify)
          }
        }) map { recommendationList => Ok(Json.toJson(recommendationList)) }
      }
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

  def upVote(recommendationId: String): Action[AnyContent] = userAction.async { implicit request =>
    Logger.info(s"Upvoting recommendation => $recommendationId")
    val email = request.user.email
    recommendationResponseRepository.getVote(email, recommendationId) flatMap { vote =>
      val recommendationResponse = RecommendationResponseRepositoryInfo(email,
        recommendationId,
        upVote = true,
        downVote = false)
      if (vote.equals("upvote")) {
        Future.successful(BadRequest("You have already upvoted the recommendation."))
      } else {
        if (vote.equals("downvote")) {
          recommendationsRepository.upVote(recommendationId, alreadyVoted = true)
        } else {
          recommendationsRepository.upVote(recommendationId, alreadyVoted = false)
        }
        recommendationResponseRepository.upsert(recommendationResponse) map { result =>
          if (result.ok) {
            Ok("Upvoted")
          } else {
            BadRequest("Something went wrong while upvoting the recommendation.")
          }
        }
      }
    }
  }

  def downVote(recommendationId: String): Action[AnyContent] = userAction.async { implicit request =>
    Logger.info(s"Downvoting recommendation => $recommendationId")
    val email = request.user.email
    recommendationResponseRepository.getVote(email, recommendationId) flatMap { vote =>
      val recommendationResponse = RecommendationResponseRepositoryInfo(email,
        recommendationId,
        upVote = false,
        downVote = true)
      if (vote.equals("downvote")) {
        Future.successful(BadRequest("You have already downvoted the recommendation"))
      } else {
        if (vote.equals("upvote")) {
          recommendationsRepository.downVote(recommendationId, alreadyVoted = true)
        } else {
          recommendationsRepository.downVote(recommendationId, alreadyVoted = false)
        }
        recommendationResponseRepository.upsert(recommendationResponse) map { result =>
          if (result.ok) {
            Ok("Downvoted")
          } else {
            BadRequest("Something went wrong while downvoting the recommendation.")
          }
        }
      }
    }
  }

  def doneRecommendation(recommendationId: String): Action[AnyContent] = adminAction.async { implicit request =>
    recommendationsRepository.doneRecommendation(recommendationId).map { result =>
      if (result.ok) {
        Ok(Json.toJson("Recommendation has been marked as Done"))
      } else {
        BadRequest(Json.toJson("Got Internal Server Error while marking the recommendation as Done"))
      }
    }
  }

  def pendingRecommendation(recommendationId: String): Action[AnyContent] = adminAction.async { implicit request =>
    recommendationsRepository.pendingRecommendation(recommendationId).map { result =>
      if (result.ok) {
        Ok(Json.toJson("Recommendation has been marked as Pending"))
      } else {
        BadRequest(Json.toJson("Got Internal Server Error while marking the recommendation as Pending"))
      }
    }
  }

}

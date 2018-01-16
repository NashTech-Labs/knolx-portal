package controllers

import java.time.LocalDateTime
import java.util.Date
import javax.inject.{Inject, Named, Singleton}

import actors.EmailActor
import akka.actor.ActorRef
import models._
import play.api.{Configuration, Logger}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONDateTime
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Recommendation(email: Option[String],
                          name: String,
                          topic: String,
                          description: String,
                          submissionDate: Option[String],
                          updateDate: Option[String],
                          approved: Option[Boolean],
                          decline: Option[Boolean],
                          pending: Boolean,
                          done: Boolean,
                          book: Boolean,
                          isLoggedIn: Boolean,
                          votes: Int,
                          upVote: Boolean,
                          downVote: Boolean,
                          id: String)

case class RecommendationInformation(email: Option[String],
                                     name: String,
                                     topic: String,
                                     description: String) {
  def validateEmail: Option[String] =
    email.fold[Option[String]](None) { userEmail =>
      if (EmailHelper.isValidEmailForGuests(userEmail) || userEmail.isEmpty) {
        None
      } else {
        Some("Entered email is not valid")
      }
    }

  def validateName: Option[String] =
    if (name.nonEmpty) {
      None
    } else {
      Some("Name must not be empty")
    }

  def validateTopic: Option[String] =
    if (topic.isEmpty) {
      Some("Topic must not be empty")
    } else if (topic.length > 140) {
      Some("Topic must be of 140 characters or less")
    } else {
      None
    }

  def validateDescription: Option[String] =
    if (description.isEmpty) {
      Some("Description must not be empty")
    } else if (description.length > 280) {
      Some("Description must be of 280 characters or less")
    } else {
      None
    }
}

@Singleton
class RecommendationController @Inject()(messagesApi: MessagesApi,
                                         recommendationsRepository: RecommendationsRepository,
                                         usersRepository: UsersRepository,
                                         controllerComponents: KnolxControllerComponents,
                                         dateTimeUtility: DateTimeUtility,
                                         configuration: Configuration,
                                         recommendationResponseRepository: RecommendationResponseRepository,
                                         @Named("EmailManager") emailManager: ActorRef
                                        ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  implicit val recommendationsFormat: OFormat[Recommendation] = Json.format[Recommendation]
  implicit val recommendationInformationFormat: OFormat[RecommendationInformation] = Json.format[RecommendationInformation]
  lazy val fromEmail: String = configuration.getOptional[String]("play.mailer.user").getOrElse("support@knoldus.com")

  def renderRecommendationPage: Action[AnyContent] = action { implicit request =>
    val email = if (!SessionHelper.isLoggedIn) Some(SessionHelper.email) else None
    Ok(views.html.recommendations.recommendation(email))
  }

  def addRecommendation(): Action[JsValue] = action(parse.json).async { implicit request =>
    request.body.validate[RecommendationInformation].asOpt.fold {
      Logger.error(s"Received a bad request for adding recommendation")
      Future.successful(BadRequest("Received a bad request due to malformed data"))
    } { recommendation =>

      if ((!SessionHelper.isLoggedIn && SessionHelper.email.equals(recommendation.email.fold("")(identity))) || SessionHelper.isLoggedIn) {

        val validatedRecommendation =
          recommendation.validateEmail orElse
            recommendation.validateName orElse
            recommendation.validateTopic orElse
            recommendation.validateDescription

        validatedRecommendation.fold {
          val recommendationInfo = RecommendationInfo(recommendation.email,
            recommendation.name,
            recommendation.topic,
            recommendation.description,
            BSONDateTime(dateTimeUtility.nowMillis),
            BSONDateTime(dateTimeUtility.nowMillis))

          recommendationsRepository.insert(recommendationInfo).map { result =>
            if (result.ok) {
              Logger.info(s"Recommendation has been successfully given by ${recommendationInfo.name}")
              usersRepository.getAllAdminAndSuperUser map {
                adminAndSuperUser =>
                  emailManager ! EmailActor.SendEmail(
                    adminAndSuperUser, fromEmail, "Knolx/Meetup Recommendation",
                    views.html.emails.recommendationnotification(recommendationInfo).toString)
                  Logger.error(s"Email has been successfully sent to admin/superUser for recommendation given by ${recommendation.name}")
              }
              Ok(Json.toJson("Your recommendation has been successfully received. Wait for approval!"))
            } else {
              Logger.info("Something went wrong while adding recommendation")
              BadRequest(Json.toJson("Recommendation could not be added due to some error"))
            }
          }
        } { errorMessage =>
          Logger.error("Recommendation submission unsuccessful with the reason " + errorMessage)
          Future.successful(BadRequest(errorMessage))
        }
      } else {
        Logger.error("Entered email did not match the email logged in with")
        Future.successful(BadRequest("Entered email did not match the email logged in with"))
      }
    }
  }

  private def checkEmail(email: Option[String]) = {
    email.fold("Anonymous") { userEmail =>
      if (userEmail.nonEmpty) userEmail else "Anonymous"
    }
  }

  def recommendationList(pageNumber: Int, filter: String = "all", sortBy: String): Action[AnyContent] = action.async { implicit request =>
    recommendationsRepository.paginate(pageNumber, filter, sortBy) flatMap { recommendations =>
      if (SessionHelper.isSuperUser || SessionHelper.isAdmin) {
        Future.sequence(recommendations map { recommendation =>
          recommendationResponseRepository.getVote(SessionHelper.email, recommendation._id.stringify) map { recommendationVote =>
            val email = checkEmail(recommendation.email)
            Recommendation(Some(email),
              recommendation.name,
              recommendation.topic,
              recommendation.description,
              Some(new Date(recommendation.submissionDate.value).toString),
              Some(new Date(recommendation.updateDate.value).toString),
              Some(recommendation.approved),
              Some(recommendation.decline),
              recommendation.pending,
              recommendation.done,
              recommendation.book,
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
            val email = checkEmail(recommendation.email)
            Recommendation(Some(email),
              recommendation.name,
              recommendation.topic,
              recommendation.description,
              Some(new Date(recommendation.submissionDate.value).toString),
              Some(new Date(recommendation.updateDate.value).toString),
              Some(recommendation.approved),
              None,
              recommendation.pending,
              recommendation.done,
              recommendation.book,
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
        Logger.info(s"Recommendation with id $recommendationId has been successfully approved")
        Ok(Json.toJson("Recommendation has been successfully approved"))
      } else {
        Logger.info(s"Something went wrong while approving recommendation with id $recommendationId")
        BadRequest(Json.toJson("Recommendation could not be approved due to some error"))
      }
    }
  }

  def declineRecommendation(recommendationId: String): Action[AnyContent] = adminAction.async { implicit request =>
    recommendationsRepository.declineRecommendation(recommendationId).map { result =>
      if (result.ok) {
        Logger.info(s"Recommendation with id $recommendationId has been successfully declined")
        Ok(Json.toJson("Recommendation has been successfully declined"))
      } else {
        Logger.info(s"Something went wrong while declining recommendation with id $recommendationId")
        BadRequest(Json.toJson("Recommendation could not be declined due to some error"))
      }
    }
  }

  def upVote(recommendationId: String): Action[AnyContent] = userAction.async { implicit request =>
    val email = request.user.email
    recommendationResponseRepository.getVote(email, recommendationId) flatMap { vote =>
      val recommendationResponse = RecommendationResponseRepositoryInfo(email,
        recommendationId,
        upVote = true,
        downVote = false)
      if (vote.equals("upvote")) {
        Logger.info(s"Recommendation with id $recommendationId was already upvoted")
        Future.successful(BadRequest("You have already upvoted the recommendation"))
      } else {
        if (vote.equals("downvote")) {
          recommendationsRepository.upVote(recommendationId, alreadyVoted = true)
        } else {
          recommendationsRepository.upVote(recommendationId, alreadyVoted = false)
        }
        recommendationResponseRepository.upsert(recommendationResponse) map { result =>
          if (result.ok) {
            Logger.info(s"Recommendation with id $recommendationId has been successfully upvoted")
            Ok("You have successfully upvoted the recommendation")
          } else {
            Logger.info(s"Something went wrong while upvoting recommendation with id $recommendationId")
            BadRequest("Recommendation could not be upvoted due to some error")
          }
        }
      }
    }
  }

  def downVote(recommendationId: String): Action[AnyContent] = userAction.async { implicit request =>
    val email = request.user.email
    recommendationResponseRepository.getVote(email, recommendationId) flatMap { vote =>
      val recommendationResponse = RecommendationResponseRepositoryInfo(email,
        recommendationId,
        upVote = false,
        downVote = true)
      if (vote.equals("downvote")) {
        Logger.info(s"Recommendation with id $recommendationId was already downvoted")
        Future.successful(BadRequest("You have already downvoted the recommendation"))
      } else {
        if (vote.equals("upvote")) {
          recommendationsRepository.downVote(recommendationId, alreadyVoted = true)
        } else {
          recommendationsRepository.downVote(recommendationId, alreadyVoted = false)
        }
        recommendationResponseRepository.upsert(recommendationResponse) map { result =>
          if (result.ok) {
            Logger.info(s"Recommendation with id $recommendationId has been successfully downvoted")
            Ok("You have successfully downVoted the recommendation")
          } else {
            Logger.info(s"Something went wrong while downvoting recommendation with id $recommendationId")
            BadRequest("Recommendation could not be downvoted due to some error")
          }
        }
      }
    }
  }

  def doneRecommendation(recommendationId: String): Action[AnyContent] = adminAction.async { implicit request =>
    recommendationsRepository.doneRecommendation(recommendationId).map { result =>
      if (result.ok) {
        Logger.info(s"Recommendation with id $recommendationId has been successfully marked as done")
        Ok(Json.toJson("Recommendation has been successfully marked as done"))
      } else {
        Logger.info(s"Something went wrong while marking recommendation with id $recommendationId as done")
        BadRequest(Json.toJson("Recommendation could not be marked as done due to some error"))
      }
    }
  }

  def pendingRecommendation(recommendationId: String): Action[AnyContent] = adminAction.async { implicit request =>
    recommendationsRepository.pendingRecommendation(recommendationId).map { result =>
      if (result.ok) {
        Logger.info(s"Recommendation with id $recommendationId has been successfully marked as pending")
        Ok(Json.toJson("Recommendation has been successfully marked as pending"))
      } else {
        Logger.info(s"Something went wrong while marking recommendation with id $recommendationId as pending")
        BadRequest(Json.toJson("Recommendation could not be marked as pending due to some error"))
      }
    }
  }

}

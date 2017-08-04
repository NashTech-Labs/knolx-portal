package controllers

import javax.inject.Inject

import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent}
import utilities.DateTimeUtility

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class FeedbackReportsController @Inject()(messagesApi: MessagesApi,
                                          mailerClient: MailerClient,
                                          usersRepository: UsersRepository,
                                          feedbackRepository: FeedbackFormsRepository,
                                          feedbackFormsResponseRepository: FeedbackFormsResponseRepository,
                                          sessionsRepository: SessionsRepository,
                                          dateTimeUtility: DateTimeUtility,
                                          controllerComponents: KnolxControllerComponents
                                         ) extends KnolxAbstractController(controllerComponents) with I18nSupport {

  def fetchReports: Action[AnyContent] = userAction.async { implicit request =>
    feedbackFormsResponseRepository
      .allDistinctSessions(request.user.email).flatMap { sessionIds =>
      sessionIds.toList match{
        case first::rest => one(first +: rest,request.user.email)
        case Nil => Future.successful(Seq(None))
      }

    }
  }

  private def one(sessionIds: List[String],presenter: String): Seq[Future[Object]] = {
    sessionIds.map { id =>
        feedbackFormsResponseRepository.mySessions(presenter, id).flatMap {
          case _::_ =>
            sessionsRepository.activeSessions.flatMap {
              case first::rest => Future.successful(two(first +: rest, id))
              case Nil => Future.successful(Seq(None))
            }
          case Nil    => Future.successful(Seq(None))
        }
      }
  }

  private def two(activeSessions : List[SessionInfo], sessionId: String): Seq[Some[(String, String)]] ={
    activeSessions.map {
      case sessionInfo if sessionInfo._id.stringify == sessionId => Some((sessionId, "ACTIVE"))
      case _                                                     => Some((sessionId, "EXPIRED"))
    }

  }


}



/*

      val mySessions =
        Future.sequence(sessionIds.toList.map { id =>
          feedbackFormsResponseRepository.mySessions(request.user.email, id).flatMap {
            case _ :: _ => {
              sessionsRepository.activeSessions.map { activeSessions =>
                //if(activeSessions.nonEmpty){
                activeSessions.map {
                  case sessionInfo if sessionInfo._id.stringify == id => Some((id, "ACTIVE"))
                  case _                                              => Some((id, "EXPIRED"))
                  //}
                  //}else{
                  //List(Some((id,"EXPIRED")))
                }
              }
            }
            case Nil    => Future.successful(List(None))
          }
        })
        mySessions.map{mySessionsIds =>
          Ok(views.html.reports.myreports(mySessionsIds.flatten.flatten))
        }


 */

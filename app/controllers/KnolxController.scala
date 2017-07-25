package controllers

import javax.inject.Inject

import play.api.http.FileMimeTypes
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc._

abstract class KnolxAbstractController(protected val controllerComponents: KnolxControllerComponents) extends KnolxBaseController

trait KnolxBaseController extends KnolxBaseControllerHelpers {

  def action: ActionBuilder[Request, AnyContent] = controllerComponents.actionBuilder

  def userAction: ActionBuilder[SecuredRequest, AnyContent] = controllerComponents.userActionBuilder

  def adminAction: ActionBuilder[SecuredRequest, AnyContent] = controllerComponents.adminActionBuilder

}

trait KnolxBaseControllerHelpers extends BaseControllerHelpers {

  protected def controllerComponents: KnolxControllerComponents

}

trait KnolxControllerComponents extends ControllerComponents {

  def userActionBuilder: ActionBuilder[SecuredRequest, AnyContent]

  def adminActionBuilder: ActionBuilder[SecuredRequest, AnyContent]

}

case class DefaultKnolxControllerComponents @Inject()(actionBuilder: DefaultActionBuilder,
                                                      userActionBuilder: UserActionBuilder,
                                                      adminActionBuilder: AdminActionBuilder,
                                                      parsers: PlayBodyParsers,
                                                      messagesApi: MessagesApi,
                                                      langs: Langs,
                                                      fileMimeTypes: FileMimeTypes,
                                                      executionContext: scala.concurrent.ExecutionContext) extends KnolxControllerComponents

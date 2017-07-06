package actors

import akka.actor.Actor
import play.api.Logger

class SessionsSupervisor extends Actor {

  override def preStart(): Unit = Logger.info("SessionsSupervisor started")

  override def postStop(): Unit = Logger.info("SessionsSupervisor stopped")

  def receive: Receive = Actor.emptyBehavior

}

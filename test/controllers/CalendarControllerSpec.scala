package controllers

import java.time.ZoneId
import java.util.TimeZone

import akka.actor.ActorRef
import com.google.inject.name.Names
import helpers.TestEnvironment
import models.{ApprovalSessionsRepository, CategoriesRepository, FeedbackFormsRepository, SessionsRepository}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.test.CSRFTokenHelper._
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.{FakeRequest, PlaySpecification}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global

class CalendarControllerSpec extends PlaySpecification with Mockito {

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    val emailManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

    lazy val controller =
      new CalendarController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        feedbackFormsRepository,
        approveSessionRepository,
        dateTimeUtility,
        config,
        knolxControllerComponent,
        emailManager
      )
    val categoriesRepository = mock[CategoriesRepository]
    val sessionsRepository = mock[SessionsRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val approveSessionRepository = mock[ApprovalSessionsRepository]

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Calendar Controller" should {

    "render calendar page" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone

      val result = controller.renderCalendarPage()(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }
  }

}

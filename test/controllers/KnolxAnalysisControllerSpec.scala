package controllers

import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId}
import java.util.{Date, TimeZone}

import akka.actor.ActorRef
import com.google.inject.name.Names
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results
import play.api.test._
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility
import play.api.test.CSRFTokenHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class KnolxAnalysisControllerSpec extends PlaySpecification with Results {

  private val date1 = new SimpleDateFormat("yyyy-MM-dd").parse("2017-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()

  private val jsonData: JsValue = Json.parse(
    """
  {
    "startDate":"2017-07-15 00:00",
    "endDate":"2017-10-15 23:59"
  }"""
  )
  private val wrongJsonData: JsValue = Json.parse(
    """
      {
      "endDate":"2017-10-15 23:59"
      } """)

  private val sessionObject = Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date1.getTime),
    "sessions", "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false, active = true, BSONDateTime(date1.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, _id)))
  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  private val ZoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))
  private val categoryList = Future.successful(List(CategoryInfo("catgeory", List("subCategory"), _id)))
  private val parseStartDate = 1500057000000L
  private val parseEndDate = 1508092140000L

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    lazy val controller =
      new KnolxAnalysisController(
        knolxControllerComponent.messagesApi,
        sessionsRepository,
        categoriesRepository,
        dateTimeUtility,
        knolxControllerComponent,
        sessionsScheduler,
        usersBanScheduler)

    val sessionsRepository: SessionsRepository = mock[SessionsRepository]
    val categoriesRepository: CategoriesRepository = mock[CategoriesRepository]
    val dateTimeUtility: DateTimeUtility = mock[DateTimeUtility]
    val sessionsScheduler =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("SessionsScheduler")))))
    val usersBanScheduler =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("UsersBanScheduler")))))

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Knolx Analysis Controller" should {

    "display knolx Analysis page" in new WithTestApplication {

      val result = controller.renderAnalysisPage()(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }

    "received Bad Request in renedering Pie Chart" in new WithTestApplication {
      val result = controller.renderPieChart(
        FakeRequest(POST, "/knolx/analysis/piechart")
          .withBody(wrongJsonData)
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "render Pie Chart" in new WithTestApplication {

      dateTimeUtility.parseDateStringToIST("2017-07-15 00:00") returns parseStartDate
      dateTimeUtility.parseDateStringToIST("2017-10-15 23:59") returns parseEndDate
      categoriesRepository.getCategories returns categoryList
      sessionsRepository.sessionsInTimeRange(FilterUserSessionInformation(None, parseStartDate, parseEndDate)) returns sessionObject

      val result = controller.renderPieChart(
        FakeRequest(POST, "/knolx/analysis/piechart")
          .withBody(jsonData)
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "received Bad Request in renedering Column Chart" in new WithTestApplication {
      val result = controller.renderColumnChart(
        FakeRequest(POST, "/knolx/analysis/piechart")
          .withBody(wrongJsonData)
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "render Column Chart" in new WithTestApplication {

      dateTimeUtility.parseDateStringToIST("2017-07-15 00:00") returns parseStartDate
      dateTimeUtility.parseDateStringToIST("2017-10-15 23:59") returns parseEndDate
      categoriesRepository.getCategories returns categoryList
      sessionsRepository.sessionsInTimeRange(FilterUserSessionInformation(None, parseStartDate, parseEndDate)) returns sessionObject

      val result = controller.renderColumnChart(
        FakeRequest(POST, "/knolx/analysis/piechart")
          .withBody(jsonData)
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "received Bad Request in renedering Line Chart" in new WithTestApplication {
      val result = controller.renderLineChart(
        FakeRequest(POST, "/knolx/analysis/piechart")
          .withBody(wrongJsonData)
          .withCSRFToken)

      status(result) must be equalTo BAD_REQUEST
    }

    "render Line Chart" in new WithTestApplication {

      dateTimeUtility.parseDateStringToIST("2017-07-15 00:00") returns parseStartDate
      dateTimeUtility.parseDateStringToIST("2017-10-15 23:59") returns parseEndDate
      categoriesRepository.getCategories returns categoryList
      sessionsRepository.sessionsInTimeRange(FilterUserSessionInformation(None, parseStartDate, parseEndDate)) returns sessionObject

      val result = controller.renderLineChart(
        FakeRequest(POST, "/knolx/analysis/piechart")
          .withBody(jsonData)
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "work" in new WithTestApplication {
      private val sessionObject1 = Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date1.getTime),
        "sessions", "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 50.00, cancelled = false, active = true, BSONDateTime(date1.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, BSONObjectID.generate()),
        SessionInfo(_id.stringify, "email", BSONDateTime(date1.getTime),
          "sessions", "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 60.00, cancelled = false, active = true, BSONDateTime(date1.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, BSONObjectID.generate()),
        SessionInfo(_id.stringify, "email1", BSONDateTime(date1.getTime),
          "sessions", "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 70.00, cancelled = false, active = true, BSONDateTime(date1.getTime), Some("youtubeURL"), Some("slideShareURL"), reminder = false, notification = false, BSONObjectID.generate())
      ))

      sessionsRepository.sessions returns sessionObject1

      val result = controller.leaderBoard(FakeRequest(GET, "knolx/analysis/leaderboard"))

      status(result) must be equalTo OK
    }

  }

}

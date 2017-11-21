package controllers

import com.typesafe.config.ConfigFactory
import models.{SessionsRepository, FeedbackFormsResponseRepository, FeedbackFormsRepository, UsersRepository}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.{Configuration, Application}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import play.api.test.{FakeRequest, PlaySpecification}
import utilities.DateTimeUtility

class HomeControllerSpec extends PlaySpecification with Mockito {

  trait TestScope extends Scope {
    val mailerClient = mock[MailerClient]
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val feedbackFormsResponseRepository = mock[FeedbackFormsResponseRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val sessionsRepository = mock[SessionsRepository]
    val usersRepository = mock[UsersRepository]

    val config = Configuration(ConfigFactory.load("application.conf"))

    lazy val controller = new HomeController(TestHelpers.stubControllerComponents(usersRepository, config))
  }

  "HomeController" should {

    "redirect index page to sessions page" in new TestScope {
      val result = controller.index(FakeRequest())

      status(result) must be equalTo SEE_OTHER
    }

  }

}

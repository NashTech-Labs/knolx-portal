package controllers

import org.specs2.mock.Mockito
import play.api.test.{FakeRequest, PlaySpecification}

class HomeControllerSpec extends PlaySpecification with Mockito {

  val homeController = new HomeController

  "HomeController" should {

    "render index page" in {
      val result = homeController.index(FakeRequest())

      status(result) must be equalTo OK

      contentAsString(result) must be contain """<title>Welcome!</title>"""
    }

  }

}

package controllers

import org.specs2.mock.Mockito
import play.api.test.{FakeRequest, PlaySpecification}

class HomeControllerSpec extends PlaySpecification with Mockito {

  val homeController = new HomeController

  "HomeController" should {

    "redirect index page to sessions page" in {
      val result = homeController.index(FakeRequest())

      status(result) must be equalTo SEE_OTHER
    }

  }

}

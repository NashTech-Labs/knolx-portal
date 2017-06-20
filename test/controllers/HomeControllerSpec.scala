package controllers

import com.google.inject.Module
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.ShouldThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{FakeRequest, Helpers, PlaySpecification}

class HomeControllerSpec extends PlaySpecification with Mockito {

  abstract class WithTestApplication(val app: Application = GuiceApplicationBuilder().disable[Module].build()) extends Around
    with Scope with ShouldThrownExpectations with Mockito {
    override def around[T: AsResult](t: => T): Result = Helpers.running(app)(AsResult.effectively(t))

    val controller = new HomeController
  }

  "HomeController" should {

    "redirect index page to sessions page" in new WithTestApplication {
      val result = controller.index(FakeRequest())

      status(result) must be equalTo SEE_OTHER
    }

  }

}

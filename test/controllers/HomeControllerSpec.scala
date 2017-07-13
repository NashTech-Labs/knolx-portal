package controllers

import models.UsersRepository
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.mvc._
import play.api.test.{FakeRequest, PlaySpecification}

class HomeControllerSpec extends PlaySpecification with Results {

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp
    val usersRepository: UsersRepository = mock[UsersRepository]

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }

    lazy val controller = new HomeController(knolxControllerComponent)
  }

  "HomeController" should {

    "redirect index page to sessions page" in new WithTestApplication {
      val result = controller.index(FakeRequest())

      status(result) must be equalTo SEE_OTHER
    }

  }

}

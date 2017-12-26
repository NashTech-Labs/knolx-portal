package controllers

import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.TimeZone

import helpers.TestEnvironment
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Application
import play.api.mvc.Results
import play.api.test.{FakeRequest, PlaySpecification}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility
import play.api.test.CSRFTokenHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SessionsCategoryControllerSpec extends PlaySpecification with Results {

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id: BSONObjectID = BSONObjectID.generate()

  private val categoryId = BSONObjectID.generate()

  private val ISTZoneId = ZoneId.of("Asia/Kolkata")
  private val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

  private val emailObject = Future.successful(Some(UserInfo("test@knoldus.com",
    "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.", "BCrypt", active = true, admin = true, coreMember = false, superUser = true, BSONDateTime(date.getTime), 0, _id)))

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()
    lazy val controller =
      new SessionsCategoryController(
        knolxControllerComponent.messagesApi,
        usersRepository,
        sessionsRepository,
        categoriesRepository,
        dateTimeUtility,
        knolxControllerComponent
      )
    val categoriesRepository = mock[CategoriesRepository]
    val sessionsRepository = mock[SessionsRepository]
    val dateTimeUtility = mock[DateTimeUtility]

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Sessions Category Controller" should {

    "render category page" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val result = controller.renderCategoryPage()(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withCSRFToken)

      status(result) must be equalTo OK
    }

    "add primary category" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      val category = "Backend"

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      categoriesRepository.insertCategory("Backend") returns updateWriteResult
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.addPrimaryCategory(category)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK
    }

    "not add primary category if DB updation was unsuccessful" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.insertCategory("Backend") returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      val category = "Backend"
      val result = controller.addPrimaryCategory(category)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add when primary category is empty" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.insertCategory("") returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      val category = ""
      val result = controller.addPrimaryCategory(category)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add when primary category already exists" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.insertCategory("Front End") returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      val category = "Front End"
      val result = controller.addPrimaryCategory(category)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "add sub category" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      val category = "Front End"
      val subCategory = "Scala"

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.getCategories returns Future(categories)

      categoriesRepository.getCategoryNameById(categoryId.stringify) returns Future(Some("Front End"))
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult

      val result = controller.addSubCategory(categoryId.stringify, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK
    }

    "not add sub category when no primary category is found for given categoryId" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.getCategories returns Future(List())

      val subCategory = "Scala"
      val result = controller.addSubCategory(categoryId.stringify, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add when sub category is empty" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      val category = "Front End"
      val subCategory = ""
      val result = controller.addSubCategory(categoryId.stringify, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add when sub category already exists" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult

      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      val category = "Front End"
      val subCategory = "Angular JS"
      val result = controller.addSubCategory(categoryId.stringify, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add sub category if primary category is not found" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      categoriesRepository.getCategoryNameById(categoryId.stringify) returns Future(None)
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult
      val category = "Front End"
      val subCategory = "Scala"
      val result = controller.addSubCategory(categoryId.stringify, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not add sub category if DB updation was unsuccessful" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      categoriesRepository.getCategoryNameById(categoryId.stringify) returns Future(Some("Front End"))
      categoriesRepository.upsert(any[CategoryInfo])(any[ExecutionContext]) returns updateWriteResult
      val category = "Front End"
      val subCategory = "Scala"
      val result = controller.addSubCategory(categoryId.stringify, subCategory)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "modify primary category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      categoriesRepository.modifyPrimaryCategory(categoryId.stringify, "front end") returns updateWriteResult
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      sessionsRepository.updateCategoryOnChange("Front End", "front end") returns updateWriteResult
      categoriesRepository.getCategories returns Future(categories)
      val result = controller.modifyPrimaryCategory(categoryId.stringify, "front end")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not modify primary category when it does not exist" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Backend", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      categoriesRepository.modifyPrimaryCategory(categoryId.stringify, "backend") returns updateWriteResult
      val result = controller.modifyPrimaryCategory(categoryId.stringify, "backend")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "not modify primary category when update primary category is empty" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      val result = controller.modifyPrimaryCategory(categoryId.stringify, "")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "not modify primary category when primary category is not found" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.modifyPrimaryCategory(categoryId.stringify, "front end") returns updateWriteResult

      sessionsRepository.updateCategoryOnChange("Front End", "front end") returns updateWriteResult
      categoriesRepository.getCategories returns Future(List())

      val result = controller.modifyPrimaryCategory(categoryId.stringify, "front end")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not modify primary category when DB updation was unsuccessful" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.modifyPrimaryCategory(categoryId.stringify, "front end") returns updateWriteResult

      sessionsRepository.updateCategoryOnChange("Front End", "front end") returns updateWriteResult
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.modifyPrimaryCategory(categoryId.stringify, "front end")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "modify sub-category" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.modifySubCategory(categoryId.stringify, "HTML", "html") returns updateWriteResult

      sessionsRepository.updateSubCategoryOnChange("HTML", "html") returns updateWriteResult
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.modifySubCategory(categoryId.stringify, "HTML", "html")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK
    }

    "not modify sub-category if new sub-category name is empty" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.modifySubCategory(categoryId.stringify, "HTML", " ")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not modify sub-category if DB updation for modify sub-category was unsuccessful" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.modifySubCategory(categoryId.stringify, "HTML", "html") returns updateWriteResult

      categoriesRepository.getCategories returns Future(categories)

      val result = controller.modifySubCategory(categoryId.stringify, "HTML", "html")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not modify sub-category if DB updation for subCategoryOnChange was unsuccessful" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val wrongUpdateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.modifySubCategory(categoryId.stringify, "HTML", "html") returns updateWriteResult

      sessionsRepository.updateSubCategoryOnChange("HTML", "html") returns wrongUpdateWriteResult
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.modifySubCategory(categoryId.stringify, "HTML", "html")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not modify sub-category if no sub-category is found" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.modifySubCategory(categoryId.stringify, "HTML", "html") returns updateWriteResult

      sessionsRepository.updateSubCategoryOnChange("HTML", "html") returns updateWriteResult
      categoriesRepository.getCategories returns Future(List())

      val result = controller.modifySubCategory(categoryId.stringify, "HTML", "html")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "not modify sub-category if sub-category already exists" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("HTML", "html", "html5"), categoryId))

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      categoriesRepository.modifySubCategory(categoryId.stringify, "HTML", "html5") returns updateWriteResult

      sessionsRepository.updateSubCategoryOnChange("HTML", "html") returns updateWriteResult
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.modifySubCategory(categoryId.stringify, "HTML", "html")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo BAD_REQUEST
    }

    "get sub-category by primary category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.getSubCategoryByPrimaryCategory("Front End")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not get sub-category by primary category when it is empty" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))

      val result = controller.getSubCategoryByPrimaryCategory("")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "delete primary category" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List(), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      sessionsRepository.updateCategoryOnChange("Front End", "") returns updateWriteResult
      categoriesRepository.deletePrimaryCategory(categoryId.stringify) returns updateWriteResult

      val result = controller.deletePrimaryCategory(categoryId.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not delete primary category when it does not exists" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List(), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      sessionsRepository.updateCategoryOnChange("Front End", "") returns updateWriteResult
      categoriesRepository.deletePrimaryCategory(categoryId.stringify) returns updateWriteResult

      val result = controller.deletePrimaryCategory(categoryId.stringify)(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "not delete primary category when it is empty" in new WithTestApplication {

      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List(), categoryId))

      val result = controller.deletePrimaryCategory("")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "delete sub-category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)
      categoriesRepository.getCategoryNameById(categoryId.stringify) returns Future(Some("Front End"))
      sessionsRepository.updateSubCategoryOnChange("Front End", "") returns updateWriteResult
      categoriesRepository.deleteSubCategory(categoryId.stringify, "HTML") returns updateWriteResult

      val result = controller.deleteSubCategory(categoryId.stringify, "HTML")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "not delete sub-category when it does not exists" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.deleteSubCategory(categoryId.stringify, "React") returns updateWriteResult
      val result = controller.deleteSubCategory(categoryId.stringify, "React")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "not delete sub-category when sub-category is empty" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.deleteSubCategory(categoryId.stringify, " ")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo BAD_REQUEST
    }

    "get topics by sub-category" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val sessionInfo = List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions", "category", "subCategory", "feedbackFormId", "topic",
        1, meetup = true, "rating", 0.00, cancelled = false, active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), temporaryYoutubeURL = Some("temporaryYoutubeURL"), reminder = false, notification = false, _id))
      sessionsRepository.getSessionByCategory("category", "subCategory") returns Future(sessionInfo)

      val result = controller.getTopicsBySubCategory("category", "subCategory")(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))
      status(result) must be equalTo OK
    }

    "get all categories" in new WithTestApplication {
      dateTimeUtility.ISTTimeZone returns ISTTimeZone
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val categories: List[CategoryInfo] = List(CategoryInfo("Front End", List("Angular JS", "HTML"), categoryId))
      categoriesRepository.getCategories returns Future(categories)

      val result = controller.getCategory()(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(result) must be equalTo OK

    }
  }

}

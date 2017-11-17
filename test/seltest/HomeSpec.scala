package seltest

import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest.{FlatSpec, ShouldMatchers}

/**
  * Created by vimalesh-mishra on 21/8/17.
  */
class HomeSpec extends FlatSpec with ShouldMatchers {

  "The Knolx-Portal Home page" should  "have the correct title" in  {
    System.setProperty("webdriver.chrome.driver", "/home/vimalesh-mishra/Pictures/chromedriver")
    val capabilities = DesiredCapabilities.chrome()
    val driver: WebDriver = new ChromeDriver(capabilities)

    val host = "http://knolx.knoldus.com/"

    /*------ Open Home Page -------*/
    driver.get(host)
    driver.manage().window().maximize()
    val siteTitle = driver.getTitle()
    driver.findElement(By.id("search-text")).sendKeys("jhfdff")
    Thread.sleep(20000)
    System.out.print("ActualTitle is " + siteTitle)
    driver.close()
  }

}

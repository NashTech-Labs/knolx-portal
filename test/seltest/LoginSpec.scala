package seltest

import java.util.concurrent.TimeUnit

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.{By, WebDriver}
import org.scalatest._


/**
  * Created by vimalesh-mishra on 28/8/17.
  */
class LoginSpec extends FlatSpec with ShouldMatchers {

  "The Knolx-Portal Login page" should "have the correct login" in {
    System.setProperty("webdriver.chrome.driver", "/home/vimalesh-mishra/Pictures/chromedriver")
    val capabilities = DesiredCapabilities.chrome()
    val driver: WebDriver = new ChromeDriver(capabilities)
    val host = "http://knolx.knoldus.com/"
    /*--- Open Home Page ---*/
    driver.get(host)
    driver.manage().window().maximize()
    Thread.sleep(5000)
    /*--- Login Page ---*/
    driver.findElement(By.xpath("/html/body/div[2]/nav/div[2]/ul/li[2]/a")).click()
    Thread.sleep(5000)
    /*--- Login with invalid email ---*/
    driver.findElement(By.name("email")).clear()
    driver.findElement(By.name("email")).sendKeys("jasdkjah@jfhkjsdf.com")
    driver.findElement(By.name("password")).clear()
    driver.findElement(By.name("password")).sendKeys("4654654")
    driver.findElement(By.xpath("//*[@id=\"loginForm\"]/div[3]/div[2]/input")).click()
    Thread.sleep(5000)
    /*--- Login with invalid credentials ---*/
    driver.findElement(By.name("email")).clear()
    driver.findElement(By.name("email")).sendKeys("vimalesh@knoldus.com")
    driver.findElement(By.name("password")).sendKeys("12376745678")
    driver.findElement(By.xpath("//*[@id=\"loginForm\"]/div[3]/div[2]/input")).click()
    Thread.sleep(5000)
    /*--- Login with valid credentials ---*/
    driver.findElement(By.name("email")).clear()
    driver.findElement(By.name("email")).sendKeys("vimalesh@knoldus.com")
    driver.findElement(By.name("password")).sendKeys("12345678")
    driver.findElement(By.xpath("//*[@id=\"loginForm\"]/div[3]/div[2]/input")).click()
    Thread.sleep(10000)
    /*--- Logout ---*/


    driver.findElement(By.xpath("/html/body/div[2]/nav/div[2]/ul/li[3]/a")).click()
    driver.manage().timeouts().implicitlyWait(2, TimeUnit.SECONDS)
    driver.findElement(By.xpath("/html/body/div[2]/nav/div[2]/ul/li[3]/ul/li[4]/a")).click()

    //Thread.sleep(10000)
    driver.close()

  }
}

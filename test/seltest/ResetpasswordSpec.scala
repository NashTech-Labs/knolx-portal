package seltest

import java.util.concurrent.TimeUnit

import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest._

/**
  * Created by vimalesh-mishra on 29/8/17.
  */
class ResetpasswordSpec extends FlatSpec with ShouldMatchers {

  "The Knolx-Portal Reset password page" should "have the resetpassword form" in {
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
    /*--- Login with valid credentials ---*/
    driver.findElement(By.name("email")).clear()
    driver.findElement(By.name("email")).sendKeys("vimalesh@knoldus.com")
    driver.findElement(By.name("password")).sendKeys("12345678")
    driver.findElement(By.xpath("//*[@id=\"loginForm\"]/div[3]/div[2]/input")).click()
    Thread.sleep(10000)
    /*--- Reset Password ---*/
    driver.findElement(By.xpath("/html/body/div[2]/nav/div[2]/ul/li[3]/a")).click()
    driver.manage().timeouts().implicitlyWait(2, TimeUnit.SECONDS)
    driver.findElement(By.xpath("/html/body/div[2]/nav/div[2]/ul/li[3]/ul/li[3]/a")).click()
    /*--- Reset Password with invalid current password ---*/



    Thread.sleep(2000)
    driver.findElement(By.name("currentPassword")).clear()
    driver.findElement(By.name("currentPassword")).sendKeys("7867868787")
    driver.findElement(By.name("newPassword")).clear()
    driver.findElement(By.name("newPassword")).sendKeys("7867868787")
    driver.findElement(By.name("confirmPassword")).clear()
    driver.findElement(By.name("confirmPassword")).sendKeys("7867868787")
    driver.findElement(By.xpath("//*[@id=\"reset-Password-Form\"]/div[4]/div[2]/input")).click()
    /*--- Reset Password with invalid new password ---*/
    Thread.sleep(2000)
    driver.findElement(By.name("currentPassword")).clear()
    driver.findElement(By.name("currentPassword")).sendKeys("PY91EO18WZ")
    driver.findElement(By.name("newPassword")).clear()
    driver.findElement(By.name("newPassword")).sendKeys("78678")
    driver.findElement(By.name("confirmPassword")).clear()
    driver.findElement(By.name("confirmPassword")).sendKeys("7867868787")
    driver.findElement(By.xpath("//*[@id=\"reset-Password-Form\"]/div[4]/div[2]/input")).click()
    /*--- Reset Password with not match to new password ---*/
    Thread.sleep(2000)
    driver.findElement(By.name("currentPassword")).clear()
    driver.findElement(By.name("currentPassword")).sendKeys("PY91EO18WZ")
    driver.findElement(By.name("newPassword")).clear()
    driver.findElement(By.name("newPassword")).sendKeys("12345678")
    driver.findElement(By.name("confirmPassword")).clear()
    driver.findElement(By.name("confirmPassword")).sendKeys("7867868787")
    driver.findElement(By.xpath("//*[@id=\"reset-Password-Form\"]/div[4]/div[2]/input")).click()
    /*--- Reset Password with new password ---*/
    Thread.sleep(2000)
    driver.findElement(By.name("currentPassword")).clear()
    driver.findElement(By.name("currentPassword")).sendKeys("12345678")
    driver.findElement(By.name("newPassword")).clear()
    driver.findElement(By.name("newPassword")).sendKeys("12345678")
    driver.findElement(By.name("confirmPassword")).clear()
    driver.findElement(By.name("confirmPassword")).sendKeys("12345678")
    driver.findElement(By.xpath("//*[@id=\"reset-Password-Form\"]/div[4]/div[2]/input")).click()

    Thread.sleep(10000)
    driver.close()

  }

}

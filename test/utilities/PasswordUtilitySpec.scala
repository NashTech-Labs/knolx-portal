package utilities

import org.scalatestplus.play.PlaySpec

class PasswordUtilitySpec extends PlaySpec {

  "Password utility" should {

    "encrypt password using BCrypt and then verify plain text and hashed password as correct" in {
      val encryptedPassword = PasswordUtility.encrypt("test!example")

      val passwordValid = PasswordUtility.isPasswordValid("test!example", encryptedPassword)

      passwordValid mustEqual true
    }

  }

}

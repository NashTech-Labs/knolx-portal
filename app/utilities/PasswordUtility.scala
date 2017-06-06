package utilities

import org.mindrot.jbcrypt

object PasswordUtility {

  val BCrypt = "BCrypt"

  def encrypt(password: String): String =
    jbcrypt.BCrypt.hashpw(password, jbcrypt.BCrypt.gensalt(10))

  def isPasswordValid(enteredPassword: String, savedPassword: String): Boolean =
    jbcrypt.BCrypt.checkpw(enteredPassword, savedPassword)

}

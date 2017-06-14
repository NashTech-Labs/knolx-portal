package utilities

import org.scalatestplus.play.PlaySpec

class EncryptionUtilitySpec extends PlaySpec {

  "Encryption utility" should {

    "encrypt string" in {
      val encryptedEmail = EncryptionUtility.encrypt("test@example.com")

      encryptedEmail mustEqual "uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU="
    }

    "decrypt string" in {
      val decryptedEmail = EncryptionUtility.decrypt("uNtgSXeM+2V+h8ChQT/PiHq70PfDk+sGdsYAXln9GfU=")

      decryptedEmail mustEqual "test@example.com"
    }

  }

}

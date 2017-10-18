package utilities

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

object EncryptionUtility {
  private val Algorithm = "AES/CBC/PKCS5Padding"
  private val Key = new SecretKeySpec(Base64.getDecoder.decode("DxVnlUlQSu3E5acRu7HPwg=="), "AES")
  private val IvSpec = new IvParameterSpec(new Array[Byte](16))

  val AdminKey = "Dx$V!nl%Ul^QS&u3*E5@acR-u7HPwg=="
  val SuperUserKey = "DV$V~nl*Ul!QS&u8*E6@acR-h7HPqg+="

  def encrypt(text: String): String = {
    val cipher = Cipher.getInstance(Algorithm)
    cipher.init(Cipher.ENCRYPT_MODE, Key, IvSpec)

    new String(Base64.getEncoder.encode(cipher.doFinal(text.getBytes("utf-8"))), "utf-8")
  }

  def decrypt(text: String): String = {
    val cipher = Cipher.getInstance(Algorithm)
    cipher.init(Cipher.DECRYPT_MODE, Key, IvSpec)

    new String(cipher.doFinal(Base64.getDecoder.decode(text.getBytes("utf-8"))), "utf-8")
  }
}

package com.example.masterproject

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionUtils {

    companion object {
        private val secureRandom = SecureRandom()
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AES_KEY_SIZE = 128


        fun calculateAESKeys(privateKey: PrivateKey, certificate: X509Certificate): SecretKey {
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(certificate.publicKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")
            val byteKey: ByteArray = Arrays.copyOf(
                sha256.digest(sharedSecret), AES_KEY_SIZE / java.lang.Byte.SIZE
            )

            return SecretKeySpec(byteKey, "AES")
        }

        fun symmetricEncryption(plaintext: String, secretKey: SecretKey): String {
            val iv = ByteArray(GCM_IV_LENGTH) //NEVER REUSE THIS IV WITH SAME KEY
            secureRandom.nextBytes(iv)

            val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv) //128 bit auth tag length

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

            val cipherText: ByteArray = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))


            val byteBuffer: ByteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherText)
            val byteArray = byteBuffer.array()

            return Base64.getEncoder().encodeToString(byteArray)

        }

        fun symmetricDecryption(cipherText: String, secretKey: SecretKey): String {
            try {
                val cipherArray = Base64.getDecoder().decode(cipherText)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                //use first 12 bytes for iv
                val gcmIv = GCMParameterSpec(GCM_TAG_LENGTH * 8, cipherArray, 0, GCM_IV_LENGTH)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv)

                //use everything from 12 bytes on as ciphertext
                val plainText =
                    cipher.doFinal(cipherArray, GCM_IV_LENGTH, cipherArray.size - GCM_IV_LENGTH)

                return String(plainText, StandardCharsets.UTF_8)
            }
            catch (e: AEADBadTagException) {
                e.printStackTrace()
                return  "AEADBadTagException could not decrypt message"
            }
        }
    }
}
package com.example.masterproject

import android.content.Context
import android.util.Log
import org.json.JSONException
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import javax.crypto.*
import kotlin.Error


class AESUtils {

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AES_KEY_SIZE = 128
        var currentKey: SecretKey? = null
        var nextKey: SecretKey? = null

        private const val TAG = "AESUtils"


        fun calculateAESKeyDH(privateKey: PrivateKey, certificate: X509Certificate): SecretKey {
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

        fun generateAESKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(AES_KEY_SIZE) // for example
            return keyGen.generateKey()

        }

        fun storeAESKey(key: SecretKey, userName: String, context: Context) {
            try {
                val keysString =
                    context.openFileInput(Constants.KEY_FILE).bufferedReader().use { it.readText() }
                val keysObject = JSONObject(keysString)
                val keyString = keyToString(key)
                keysObject.put(userName, keyString)
                context.openFileOutput(Constants.KEY_FILE, Context.MODE_PRIVATE).use {
                    it.write(keysObject.toString().toByteArray())
                }
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "keyList file not found")
                val keysObject = JSONObject()
                val keyString = keyToString(key)
                keysObject.put(userName, keyString)
                context.openFileOutput(Constants.KEY_FILE, Context.MODE_PRIVATE).use {
                    it.write(keysObject.toString().toByteArray())
                }
            }
        }

        private fun fetchStoredAESKey(userName: String, context: Context): SecretKey? {
            return try {
                val keysString =
                    context.openFileInput(Constants.KEY_FILE).bufferedReader().use { it.readText() }
                val keysObject = JSONObject(keysString)
                Log.d(TAG, "FETCHED KEY: ${keysObject.getString(userName)}")
                stringToKey(keysObject.getString(userName))
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "keyList file not found")
                null
            } catch (e: JSONException) {
                Log.w(TAG, "Could not find stored key for user $userName")
                null
            }
        }

        fun deleteAllStoredKeys(context: Context) {
            try {
                currentKey = null
                nextKey = null
                context.deleteFile(Constants.KEY_FILE)
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "keyList file not found")
            }
        }

        fun getEncryptionKey(userName: String, context: Context): SecretKey {
            /*
            return fetchStoredAESKey(userName, context) ?: calculateAESKeyDH(
                Utils.privateKey!!,
                Ledger.getLedgerEntry(userName!!)!!.certificate
            )
             */
            return currentKey ?: calculateAESKeyDH(
                Utils.privateKey!!,
                Ledger.getLedgerEntry(userName!!)!!.certificate
            )
        }

        fun keyToString(key: SecretKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }

        fun stringToKey(string: String): SecretKey {
            val keyString = Base64.getDecoder().decode(string)
            return SecretKeySpec(keyString, 0, keyString.size, "AES");
        }

        fun symmetricEncryption(plaintext: String, secretKey: SecretKey): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv.copyOf()
            val cipherText = cipher.doFinal(plaintext.toByteArray())
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

                Log.d("DECRYPT KEY", String(secretKey.encoded))
                //use everything from 12 bytes on as ciphertext
                val plainText =
                    cipher.doFinal(cipherArray, GCM_IV_LENGTH, cipherArray.size - GCM_IV_LENGTH)

                return String(plainText, StandardCharsets.UTF_8)
            } catch (e: AEADBadTagException) {
                e.printStackTrace()
                return "NOT ABLE TO DECRYPT MESSAGE AEADBadTagException"
            }
        }
    }
}
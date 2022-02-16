package com.example.masterproject.utils

import android.content.Context
import android.util.Log
import com.example.masterproject.ledger.LedgerEntry
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.*
import java.util.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.*


class AESUtils {

    data class KeyMaterial(val keyMaterial: Key, val isPrivateKey: Boolean)

    companion object {
        var keyMaterialMap: MutableMap<String, KeyMaterial> = mutableMapOf()
        val symmetricKeyStore: KeyStore = loadSymmetricKeyStore()

        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AES_KEY_SIZE = 128

        private const val TAG = "AESUtils"

        fun setKeyForUser(username: String, key: SecretKey) {
            symmetricKeyStore.setKeyEntry(username, key, Constants.KEYSTORE_PASSWORD.toCharArray(), null)
        }

        fun getKeyForUser(username: String): SecretKey? {
            return try{
                symmetricKeyStore.getKey(username, Constants.KEYSTORE_PASSWORD.toCharArray()) as SecretKey
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun loadSymmetricKeyStore(): KeyStore {
            val localTrustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            return try{
                val fileInputStream = FileInputStream(Constants.SYMMETRIC_KEYSTORE_PATH)
                localTrustStore.load(fileInputStream, Constants.SYMMETRIC_KEYSTORE_PATH.toCharArray())
                Log.d(TAG, "SYMMETRIC KEYSTORE LOADED")
                localTrustStore as KeyStore
            } catch (e: FileNotFoundException) {
                localTrustStore.load(null, Constants.SYMMETRIC_KEYSTORE_PATH.toCharArray())
                Log.d(TAG, "NEW SYMMETRIC KEYSTORE CREATED")
                localTrustStore as KeyStore
            }
        }

        fun calculateAESKeyDH(privateKey: PrivateKey, publicKey: PublicKey): SecretKey {
            val keyAgreement = KeyAgreement.getInstance(Constants.KEY_TYPE)
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(publicKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            val sha256: MessageDigest = MessageDigest.getInstance(Constants.MESSAGE_DIGEST_HASH)
            val byteKey: ByteArray = Arrays.copyOf(
                sha256.digest(sharedSecret), AES_KEY_SIZE / java.lang.Byte.SIZE
            )

            return SecretKeySpec(byteKey, Constants.SYMMETRIC_ENCRYPTION_ALGORITHM)
        }

        fun deleteAllStoredKeys() {
            try {
                keyMaterialMap.clear()
                for (alias in symmetricKeyStore.aliases()) {
                    symmetricKeyStore.deleteEntry(alias)
                }
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "keyList file not found")
            }
        }

        fun stringToKey(string: String): SecretKey {
            val keyString = Base64.getDecoder().decode(string)
            return SecretKeySpec(keyString, 0, keyString.size, Constants.SYMMETRIC_ENCRYPTION_ALGORITHM);
        }

        fun symmetricEncryption(plaintext: String, secretKey: SecretKey): String {
            val cipher = Cipher.getInstance(Constants.SYMMETRIC_ENCRYPTION_TRANSFORMATION)
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

                val cipher = Cipher.getInstance(Constants.SYMMETRIC_ENCRYPTION_TRANSFORMATION)
                //use first 12 bytes for iv
                val gcmIv = GCMParameterSpec(GCM_TAG_LENGTH * 8, cipherArray, 0, GCM_IV_LENGTH)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv)

                Log.d(TAG, "Decryption key: ${String(secretKey.encoded)}")
                //use everything from 12 bytes on as ciphertext
                val plainText =
                    cipher.doFinal(cipherArray, GCM_IV_LENGTH, cipherArray.size - GCM_IV_LENGTH)

                return String(plainText, StandardCharsets.UTF_8)
            } catch (e: AEADBadTagException) {
                Log.d(TAG, "Error decrypting message")
                e.printStackTrace()
                return "Error decrypting message"
            }
        }

    }
}
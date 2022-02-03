package com.example.masterproject.utils

import android.content.Context
import android.util.Log
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.network.TLSClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.*
import kotlin.NoSuchElementException


class AESUtils {

    data class SymmetricKeyEntry(val currentKey: SecretKey?, val nextKey: SecretKey?)

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AES_KEY_SIZE = 128
        var keyMap: MutableMap<String, SymmetricKeyEntry> = mutableMapOf()

        private const val TAG = "AESUtils"

        fun getCurrentKeyForUser(userName: String): SecretKey? {
            if(keyMap.containsKey(userName)){
                return keyMap.getValue(userName).currentKey
            }
            return null
        }

        fun getNextKeyForUser(userName: String): SecretKey? {
            if(keyMap.containsKey(userName)){
                return keyMap.getValue(userName).nextKey
            }
            return null
        }

        fun useNextKeyForUser(userName: String): SecretKey? {
            val symmetricKeyEntry = keyMap.getValue(userName)
            val nextKey = symmetricKeyEntry.nextKey
            if(symmetricKeyEntry.nextKey != null) {
                keyMap[userName] = SymmetricKeyEntry(nextKey, null)
            }
            return nextKey
        }

        fun setNextKeyForUser(userName: String, nextKey: SecretKey) {
            try{
                val symmetricKeyEntry = keyMap.getValue(userName)
                keyMap[userName] = SymmetricKeyEntry(symmetricKeyEntry.currentKey, nextKey)
            } catch (e: NoSuchElementException) {
                keyMap[userName] = SymmetricKeyEntry(null, nextKey)
            }
        }

        fun setCurrentKeyForUser(userName: String, currentKey: SecretKey) {
            keyMap[userName] = SymmetricKeyEntry(currentKey, null)
        }

        fun getEncryptionKey(userName: String, context: Context): SecretKey {
            return getCurrentKeyForUser(userName) ?: calculateAESKeyDH(
                PKIUtils.getPrivateKey(context)!!,
                Ledger.getLedgerEntry(userName)!!.certificate
            )
        }

        fun calculateAESKeyDH(privateKey: PrivateKey, certificate: X509Certificate): SecretKey {
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(certificate.publicKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")
            val byteKey: ByteArray = Arrays.copyOf(
                sha256.digest(sharedSecret), AES_KEY_SIZE / java.lang.Byte.SIZE
            )

            val secretKey = SecretKeySpec(byteKey, "AES")
            setCurrentKeyForUser(PKIUtils.getUsernameFromCertificate(certificate), secretKey)
            return secretKey
        }

        fun generateAESKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(AES_KEY_SIZE)
            return keyGen.generateKey()

        }

        fun deleteAllStoredKeys(context: Context) {
            try {
                keyMap.clear()
                context.deleteFile(Constants.KEY_FILE)
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "keyList file not found")
            }
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

        fun symmetricDecryption(cipherText: String, secretKey: SecretKey, ledgerEntry: LedgerEntry): String {
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
                //Keys are probably out of sync
                try{
                    val sharedKey = calculateAESKeyDH(PKIUtils.privateKey!!, ledgerEntry.certificate)
                    val cipherArray = Base64.getDecoder().decode(cipherText)

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    //use first 12 bytes for iv
                    val gcmIv = GCMParameterSpec(GCM_TAG_LENGTH * 8, cipherArray, 0, GCM_IV_LENGTH)
                    cipher.init(Cipher.DECRYPT_MODE, sharedKey, gcmIv)

                    Log.d("DECRYPT KEY", String(sharedKey.encoded))
                    //use everything from 12 bytes on as ciphertext
                    val plainText =
                        cipher.doFinal(cipherArray, GCM_IV_LENGTH, cipherArray.size - GCM_IV_LENGTH)

                    return String(plainText, StandardCharsets.UTF_8)
                } catch (e: AEADBadTagException) {
                    //resyncKeys(ledgerEntry)
                    e.printStackTrace()
                    return "ERROR DECRYPTING MESSAGE, please restart chat - resync request sent"
                }
            }
        }

        /*
        fun resyncKeys(ledgerEntry: LedgerEntry){
            val currentKey = calculateAESKeyDH(PKIUtils.privateKey!!, ledgerEntry.certificate)
            val nextKey = generateAESKey()
            setNextKeyForUser(PKIUtils.getUsernameFromCertificate(ledgerEntry.certificate), nextKey)
            GlobalScope.launch(Dispatchers.IO) {
                TLSClient.sendKeyDelivery(ledgerEntry, nextKey, currentKey)
            }
        }

         */
    }
}
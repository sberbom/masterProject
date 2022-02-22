package com.example.masterproject.crypto

import android.util.Log
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.Constants
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.lang.Exception
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class Ratchet(private val username: String) {

    private var rootRatchet: Int = 1
    var sendingRatchet: Int = 1
    var lastKey: Int = 0
    private val TAG = "Ratchet"

   private fun calculateNextKey(currentKey: SecretKey): SecretKey {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val hkdfParam = HKDFParameters(currentKey.encoded, null, null)
        hkdf.init(hkdfParam)
        val okm = ByteArray(32)
        hkdf.generateBytes(okm, 0, Constants.AES_KEY_SIZE / java.lang.Byte.SIZE)
        return SecretKeySpec(okm, Constants.SYMMETRIC_ENCRYPTION_ALGORITHM)
    }

    private fun generateKey(keyRound: Int): SecretKey {
        val previousKey = if(keyRound == 0 ) {
            AESUtils.getKeyForUser(username)
        }else {
            AESUtils.getKeyForUser("${username}${keyRound - 1}") ?: generateKey(keyRound - 1)
        }
        val key = calculateNextKey(previousKey!!)
        AESUtils.setKeyForUser("${username}$keyRound", key)
        return key
    }

    fun getKey(keyRound: Int): SecretKey{
        if(keyRound > lastKey) {
            lastKey = keyRound
        }
        return AESUtils.getKeyForUser("${username}${keyRound}") ?: generateKey(keyRound)
    }

    fun getEncryptionKey(): SecretKey {
        sendingRatchet++;
        return getKey(sendingRatchet - 1)
    }


    fun clean() {
        for(i in 1..lastKey){
            try {
                AESUtils.deleteKey("${username}sending$i")
            }
            catch (e: Exception){ }
        }
    }

}
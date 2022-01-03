package com.example.masterproject

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import javax.crypto.Cipher

class Utils {

    companion object{
        var keyPair: KeyPair? = null

        fun encryptMessage(plainText: String, publickey: PublicKey): String {
            val cipher = Cipher.getInstance("ECIES")
            cipher.init(Cipher.ENCRYPT_MODE, publickey)
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray()))
        }

        fun decryptMessage(encryptedText: String?, privatekey: PrivateKey): String {
            val cipher = Cipher.getInstance("ECIES")
            cipher.init(Cipher.DECRYPT_MODE, privatekey)
            return String(cipher.doFinal(Base64.getDecoder().decode(encryptedText)))
        }
    }
}
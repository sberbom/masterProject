package com.example.masterproject

import java.lang.Exception
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
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

        fun encryptionKeyToString(key: PublicKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }

        fun stringToEncryptionKey(key: String): PublicKey {
            val encKey = Base64.getDecoder().decode(key)
            val publicKeySpec = X509EncodedKeySpec(encKey)
            val keyFactory = KeyFactory.getInstance("EC")
            return keyFactory.generatePublic(publicKeySpec)
        }

        fun getMyIpAddress(): String? {
            try {
                val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val networkInterface: NetworkInterface = en.nextElement()
                    val enumIpAddress: Enumeration<InetAddress> = networkInterface.inetAddresses
                    while (enumIpAddress.hasMoreElements()) {
                        val inetAddress: InetAddress = enumIpAddress.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.hostAddress
                        }
                    }
                }
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}
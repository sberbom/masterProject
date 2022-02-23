package com.example.masterproject.network.unicast

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.crypto.Ratchet
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.PKIUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.PrivateKey
import java.security.PublicKey

abstract class NetworkSocket: Thread() {

    abstract val TAG: String
    open var ratchet: Ratchet? = null
    open var inputStream: DataInputStream? = null
    open var outputStream: DataOutputStream? = null
    var running: Boolean = true
    var peerFirstKeyMaterial: PublicKey? = null
    var myFirstKeyMaterial: PrivateKey? = null

    abstract fun decryptMessagePayload(networkMessage: NetworkMessage, ledgerEntry: LedgerEntry): String
    abstract fun encryptMessageSymmetric(message: String ): String
    abstract fun getRatchetKeyRound(): Int

    fun sendMessage(message: String, messageType: String) {
        if (outputStream == null) {
            Log.d(TAG, "Output stream is null")
        } else {
            try {
                val myUsername = PKIUtils.getUsernameFromCertificate(PKIUtils.getStoredCertificate()!!)
                val ratchetKeyRound = getRatchetKeyRound()
                val messageEncrypted = encryptMessageSymmetric(message)
                val messageToSend = NetworkMessage(myUsername, messageEncrypted, messageType, "", 0, 0, 0, ratchetKeyRound).toString()
                Log.d(TAG, "Message sent $messageToSend")
                outputStream!!.writeUTF(messageToSend)
                outputStream!!.flush()
                if(messageType == UnicastMessageTypes.CHAT_MESSAGE.toString()) {
                    Handler(Looper.getMainLooper()).post {
                        ChatActivity.addChat("You:", message)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                running = false
            }
        }
    }

    fun sendKeyMaterial(username: String) {
        val peerRootKey = AESUtils.getKeyForUser(username)
        if(peerRootKey == null) {
            sendFirstKeyMaterial(username)
        }
        else {
            val peerKeyMaterial = AESUtils.keyMaterialMap[username]?.peerPublicKey
            if (peerKeyMaterial != null) {
                val myKeyMaterial = PKIUtils.generateECKeyPair()
                val keyMaterialString = PKIUtils.encryptionKeyToString(myKeyMaterial.public)
                val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial.private, peerKeyMaterial)
                AESUtils.setKeyForUser(username, sharedKey)
                AESUtils.keyMaterialMap[username] =
                    AESUtils.KeyMaterial(myKeyMaterial.private, null)
                sendMessage(keyMaterialString, UnicastMessageTypes.KEY_MATERIAL.toString())
            }
        }
    }

    fun handleKeyMaterialDelivery(keyMaterialString: String, username: String) {
        val peerRootKey = AESUtils.getKeyForUser(username)
        if(peerRootKey == null) {
            handleFirstKeyMaterialDelivery(keyMaterialString, username)
        }
        else {
            val receivedKeyMaterial = PKIUtils.stringToEncryptionKey(keyMaterialString)
            val myKeyMaterial = AESUtils.keyMaterialMap[username]?.myPrivateKey
            if (myKeyMaterial != null) {
                val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial, receivedKeyMaterial)
                AESUtils.setKeyForUser(username, sharedKey)
                AESUtils.keyMaterialMap[username] = AESUtils.KeyMaterial(null, receivedKeyMaterial)
            }
        }
    }

    private fun sendFirstKeyMaterial(username: String) {
        val myKeyMaterial = PKIUtils.generateECKeyPair()
        val keyMaterialString = PKIUtils.encryptionKeyToString(myKeyMaterial.public)
        if(peerFirstKeyMaterial != null) {
            val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial.private, peerFirstKeyMaterial!!)
            AESUtils.setKeyForUser(username, sharedKey)
            peerFirstKeyMaterial = null
        }
        else{
            myFirstKeyMaterial = myKeyMaterial.private
        }
        sendMessage(keyMaterialString, UnicastMessageTypes.KEY_MATERIAL.toString())

    }

    private fun handleFirstKeyMaterialDelivery(keyMaterialString: String, username: String) {
        val receivedKeyMaterial = PKIUtils.stringToEncryptionKey(keyMaterialString)
        if(myFirstKeyMaterial != null) {
            val sharedKey = AESUtils.calculateAESKeyDH(myFirstKeyMaterial!!, receivedKeyMaterial)
            AESUtils.setKeyForUser(username, sharedKey)
            myFirstKeyMaterial = null
        }
        else {
            peerFirstKeyMaterial = receivedKeyMaterial
            sendFirstKeyMaterial(username)
        }
    }
}
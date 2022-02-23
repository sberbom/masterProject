package com.example.masterproject.network.unicast

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.crypto.Ratchet
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.PKIUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import javax.crypto.SecretKey

abstract class Server(): Thread() {

    abstract val TAG: String
    var running: Boolean = true
    var username: String? = null
    var encryptionKey: SecretKey? = null
    val context: Context? = App.getAppContext()
    abstract val inputStream: DataInputStream
    abstract val outputStream: DataOutputStream
    var ratchet: Ratchet? = null
    private var sentKeyMaterial = false

    abstract fun setEncryptionKeyAndUsername(networkMessage: NetworkMessage)
    abstract fun decryptMessagePayload(networkMessage: NetworkMessage, ledgerEntry: LedgerEntry): String
    abstract fun encryptMessageSymmetric(message: String ): String
    abstract fun getRatchetKeyRound(): Int

    override fun run(){
        Log.d(TAG, "Starting $TAG")
        try {
            while (running) {
                val receivedMessage = inputStream.readUTF()
                Log.d(TAG, "Message received: $receivedMessage")
                val networkMessage = NetworkMessage.decodeNetworkMessage(receivedMessage)
                val ledgerEntry = Ledger.getLedgerEntry(networkMessage.sender)
                setEncryptionKeyAndUsername(networkMessage)
                if (!sentKeyMaterial) {
                    sendKeyMaterial(networkMessage.sender)
                    sentKeyMaterial = true
                }
                val messagePayload = decryptMessagePayload(networkMessage, ledgerEntry!!)
                when (networkMessage.messageType) {
                    UnicastMessageTypes.CLIENT_HELLO.toString() -> {}
                    UnicastMessageTypes.KEY_MATERIAL.toString() -> handleKeyMaterialDelivery(messagePayload, networkMessage.sender)
                    UnicastMessageTypes.KEY_DELIVERY.toString() -> {
                        handleKeyDelivery(
                            messagePayload,
                            networkMessage.sender
                        )
                    }
                    UnicastMessageTypes.GOODBYE.toString() -> handleGoodbye()
                    else -> {
                        Handler(Looper.getMainLooper()).post {
                            ChatActivity.addChat(networkMessage.sender, messagePayload)
                        }
                    }
                }
            }
        }
        catch (e: EOFException) {
            Log.d(TAG, "Stream closed unexpectedly")
            handleGoodbye()
        }
    }

    fun sendMessage(message: String, messageType: String) {
        try {
            val username = PKIUtils.getUsernameFromCertificate(PKIUtils.getStoredCertificate()!!)
            val ratchetKeyRound = getRatchetKeyRound()
            val encryptedMessage = encryptMessageSymmetric(message)
            val messageToSend = NetworkMessage(username, encryptedMessage, messageType, "", 0, ratchetKeyRound).toString()
            Log.d(TAG, "Message sendt $messageToSend")
            outputStream.writeUTF(messageToSend)
            outputStream.flush()
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

    open fun handleGoodbye() {
        running = false
        ServerMap.serverMap.remove(username)
        ratchet?.clean()
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context!!.startActivity(intent)
    }

    private fun sendKeyMaterial(username: String) {
        val peerKeyMaterial = AESUtils.keyMaterialMap[username]?.peerPublicKey
        if(peerKeyMaterial != null) {
            val myKeyMaterial = PKIUtils.generateECKeyPair()
            val keyMaterialString = PKIUtils.encryptionKeyToString(myKeyMaterial.public)
            val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial.private, peerKeyMaterial)
            AESUtils.setKeyForUser(username, sharedKey)
            AESUtils.keyMaterialMap[username] = AESUtils.KeyMaterial(myKeyMaterial.private, null)
            sendMessage(keyMaterialString, UnicastMessageTypes.KEY_MATERIAL.toString())
        }
    }

    private fun handleKeyMaterialDelivery(keyMaterialString: String, username: String) {
        val receivedKeyMaterial = PKIUtils.stringToEncryptionKey(keyMaterialString)
        val myKeyMaterial = AESUtils.keyMaterialMap[username]?.myPrivateKey
        if(myKeyMaterial != null) {
            val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial, receivedKeyMaterial)
            AESUtils.setKeyForUser(username, sharedKey)
            AESUtils.keyMaterialMap[username] = AESUtils.KeyMaterial(null, receivedKeyMaterial)
        }
    }

    private fun handleKeyDelivery(key : String, username: String) {
        try {
            Log.d(TAG, "Encryption key received $key")
            val nextKey = AESUtils.stringToKey(key)
            AESUtils.setKeyForUser(username, nextKey)
        }
        catch (e: IllegalArgumentException) {
            Log.d(TAG, key)
            e.printStackTrace()
        }
    }
}
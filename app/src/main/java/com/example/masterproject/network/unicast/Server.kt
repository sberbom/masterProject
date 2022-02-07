package com.example.masterproject.network.unicast

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.activities.MainActivity
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

    abstract fun setEncryptionKeyAndUsername(networkMessage: NetworkMessage)
    abstract fun decryptMessagePayload(networkMessage: NetworkMessage, encryptionKey: SecretKey?, ledgerEntry: LedgerEntry): String
    abstract fun encryptMessageSymmetric(message: String, encryptionKey: SecretKey?): String

    override fun run(){
        Log.d(TAG, "Starting $TAG")
        try {
            while (running) {
                val receivedMessage = inputStream.readUTF()
                Log.d(TAG, "Message received: $receivedMessage")
                val networkMessage = NetworkMessage.decodeNetworkMessage(receivedMessage)
                val ledgerEntry = Ledger.getLedgerEntry(networkMessage.sender)
                setEncryptionKeyAndUsername(networkMessage)
                val messagePayload = decryptMessagePayload(networkMessage, encryptionKey, ledgerEntry!!)
                when (networkMessage.messageType) {
                    UnicastMessageTypes.CLIENT_HELLO.toString() -> {}
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
            val encryptedMessage = encryptMessageSymmetric(message, encryptionKey)
            val messageToSend = NetworkMessage(username, encryptedMessage, messageType).toString()
            Log.d(TAG, "Message sendt $messageToSend")
            outputStream.writeUTF(messageToSend)
            outputStream.flush()
            Handler(Looper.getMainLooper()).post {
                ChatActivity.addChat("You:", message)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            running = false
        }
    }

    open fun handleGoodbye() {
        running = false
        ServerMap.serverMap.remove(username)
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context!!.startActivity(intent)
    }

    private fun handleKeyDelivery(key : String, username: String) {
        try {
            Log.d(TAG, "Encryption key received $key")
            val nextKey = AESUtils.stringToKey(key)
            AESUtils.setCurrentKeyForUser(username, nextKey)
        }
        catch (e: IllegalArgumentException) {
            Log.d(TAG, key)
            e.printStackTrace()
        }
    }
}
package com.example.masterproject.network

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.PKIUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import javax.crypto.SecretKey

class TCPServer(private val inputStream: DataInputStream, private val outputStream: DataOutputStream): Server() {
    private val TAG = "TLSServer"
    private var running = true
    private var username: String? = null
    private var encryptionKey: SecretKey? = null
    private val context = App.getAppContext()

    override fun run(){
        Log.d(TAG, "Starting TCPServer")
        try {
            while (running) {
                val receivedMessage = inputStream.readUTF()
                Log.d(TAG, "Message received: $receivedMessage")
                val networkMessage = NetworkMessage.decodeNetworkMessage(receivedMessage)
                val ledgerEntry = Ledger.getLedgerEntry(networkMessage.sender)
                if(encryptionKey == null || username == null) {
                    encryptionKey = AESUtils.getCurrentKeyForUser(networkMessage.sender)
                    username = networkMessage.sender
                }
                when (networkMessage.messageType) {
                    UnicastMessageTypes.CLIENT_HELLO.toString() -> {
                    }
                    UnicastMessageTypes.KEY_DELIVERY.toString() -> {
                        val decryptedMessage = AESUtils.symmetricDecryption(networkMessage.payload, encryptionKey!!, ledgerEntry!!)
                        handleKeyDelivery(
                            decryptedMessage,
                            networkMessage.sender
                        )
                    }
                    UnicastMessageTypes.GOODBYE.toString() -> handleGoodbye()
                    else -> {
                        val decryptedMessage = AESUtils.symmetricDecryption(
                            networkMessage.payload,
                            encryptionKey!!,
                            ledgerEntry!!
                        )
                        Handler(Looper.getMainLooper()).post {
                            ChatActivity.addChat(networkMessage.sender, decryptedMessage)
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

    override fun sendMessage(message: String, messageType: String) {
        try {
            val username = PKIUtils.getUsernameFromCertificate(PKIUtils.getStoredCertificate()!!)
            val encryptedMessage = AESUtils.symmetricEncryption(message, encryptionKey!!)
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

    private fun handleGoodbye() {
        AESUtils.useNextKeyForUser(username!!)
        ServerMap.serverMap.remove(username)
        inputStream.close()
        outputStream.close()
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context!!.startActivity(intent)
    }

    private fun handleKeyDelivery(key : String, username: String) {
        try {
            Log.d(TAG, "Encryption key received $key")
            val nextKey = AESUtils.stringToKey(key)
            AESUtils.setNextKeyForUser(username, nextKey)
        }
        catch (e: IllegalArgumentException) {
            Log.d(TAG, key)
            e.printStackTrace()
        }
    }
}
package com.example.masterproject.network

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.PKIUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

class TLSServer(private val outputStream: DataOutputStream, private val inputStream: DataInputStream): Server() {

    private val TAG = "TLSServer"
    private var running = true
    private val context = App.getAppContext()
    private var username: String? = null

    override fun run(){
        Log.d(TAG, "Starting TLSServer")
        try {
            while (running) {
                val receivedMessage = inputStream.readUTF()
                Log.d(TAG, "Message received: $receivedMessage")
                val networkMessage = NetworkMessage.decodeNetworkMessage(receivedMessage)
                if(username == null) {
                    username = networkMessage.sender
                }
                when(networkMessage.messageType) {
                    UnicastMessageTypes.CLIENT_HELLO.toString() -> {}
                    UnicastMessageTypes.KEY_DELIVERY.toString() -> handleKeyDelivery(networkMessage.payload, networkMessage.sender)
                    UnicastMessageTypes.GOODBYE.toString() -> handleGoodbye()
                    else -> {
                        Handler(Looper.getMainLooper()).post {
                            ChatActivity.addChat(networkMessage.sender, networkMessage.payload)
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
            val username = PKIUtils.getUsernameFromCertificate(PKIUtils.getCertificate()!!)
            val messageToSend = NetworkMessage(username, message, messageType).toString()
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
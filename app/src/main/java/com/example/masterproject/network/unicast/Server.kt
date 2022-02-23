package com.example.masterproject.network.unicast

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.types.NetworkMessage
import java.io.EOFException
import javax.crypto.SecretKey

abstract class Server: NetworkSocket() {

    var encryptionKey: SecretKey? = null
    var username: String? = null
    private var sentKeyMaterial = false

    abstract fun setEncryptionKeyAndUsername(networkMessage: NetworkMessage)

    override fun run(){
        Log.d(TAG, "Starting $TAG")
        try {
            while (running) {
                if(inputStream != null) {
                    val receivedMessage = inputStream!!.readUTF()
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
                        UnicastMessageTypes.CLIENT_HELLO.toString() -> { }
                        UnicastMessageTypes.KEY_MATERIAL.toString() -> handleKeyMaterialDelivery(messagePayload, networkMessage.sender)
                        UnicastMessageTypes.GOODBYE.toString() -> handleGoodbye()
                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                ChatActivity.addChat(networkMessage.sender, messagePayload)
                            }
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

    open fun handleGoodbye() {
        running = false
        ServerMap.serverMap.remove(username)
        ratchet?.clean()
        val context = App.getAppContext()
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context!!.startActivity(intent)
    }

}
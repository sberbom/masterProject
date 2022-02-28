package com.example.masterproject.network.unicast

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.types.UnicastPacket
import java.io.EOFException
import javax.crypto.SecretKey

abstract class Server: NetworkSocket() {

    var encryptionKey: SecretKey? = null
    var username: String? = null
    private var sentKeyMaterial = false

    abstract fun setEncryptionKeyAndUsername(unicastPacket: UnicastPacket)

    override fun run(){
        Log.d(TAG, "Starting $TAG")
        try {
            while (running) {
                if(inputStream != null) {
                    val receivedMessage = inputStream!!.readUTF()
                    Log.d(TAG, "Message received: $receivedMessage")
                    val unicastPacket = UnicastPacket.decodeUnicastPacket(receivedMessage)
                    val ledgerEntry = Ledger.getLedgerEntry(unicastPacket.sender)
                    setEncryptionKeyAndUsername(unicastPacket)
                    if (!sentKeyMaterial) {
                        sendKeyMaterial(unicastPacket.sender)
                        sentKeyMaterial = true
                    }
                    val messagePayload = decryptMessagePayload(unicastPacket, ledgerEntry!!)
                    when (unicastPacket.messageType) {
                        UnicastMessageTypes.CLIENT_HELLO.toString() -> { }
                        UnicastMessageTypes.KEY_MATERIAL.toString() -> handleKeyMaterialDelivery(messagePayload, unicastPacket.sender)
                        UnicastMessageTypes.GOODBYE.toString() -> handleGoodbye()
                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                ChatActivity.addChat(unicastPacket.sender, messagePayload)
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
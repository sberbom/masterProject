package com.example.masterproject.network.unicast

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import javax.crypto.SecretKey

abstract class Client: NetworkSocket() {


    private lateinit var clientSocket: Socket
    abstract val encryptionKey: SecretKey?
    abstract val ledgerEntry: LedgerEntry
    abstract val port: Int

    abstract fun createClientSocket(serverAddress: InetAddress): Socket

    override fun run() {
        Log.d(TAG, "Starting $TAG")
        try {
            while(running && !isInterrupted){
                val serverAddress = InetAddress.getByName(ledgerEntry.getIpAddress())
                clientSocket = createClientSocket(serverAddress)
                outputStream = DataOutputStream(clientSocket.getOutputStream())
                inputStream = DataInputStream(clientSocket.getInputStream())

                sendMessage("", UnicastMessageTypes.CLIENT_HELLO.toString())
                sendKeyMaterial(ledgerEntry.userName)

                while (running) {
                    val receivedMessage = inputStream?.readUTF()
                    if(receivedMessage != null) {
                        Log.d(TAG, "Received message: $receivedMessage")
                        val networkMessage = NetworkMessage.decodeNetworkMessage(receivedMessage)
                        val messagePayload = decryptMessagePayload(networkMessage, ledgerEntry)
                        when (networkMessage.messageType) {
                            UnicastMessageTypes.CLIENT_HELLO.toString() -> { }
                            UnicastMessageTypes.GOODBYE.toString() -> { }
                            UnicastMessageTypes.KEY_MATERIAL.toString() -> handleKeyMaterialDelivery(messagePayload, ledgerEntry.userName)
                            else -> {
                                Handler(Looper.getMainLooper()).post {
                                    ChatActivity.addChat(networkMessage.sender, messagePayload)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    open fun closeSocket() {
        ratchet?.clean()
        clientSocket.close()
    }
}
package com.example.masterproject.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.Constants
import com.example.masterproject.utils.PKIUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket


class TLSClient(private val ledgerEntry: LedgerEntry): Client() {

    override lateinit var outputStream: DataOutputStream
    override lateinit var inputStream: DataInputStream
    override lateinit var clientSocket: SSLSocket
    private val sslContext: SSLContext = SSLContext.getDefault()
    private val TAG = "TCPClient"
    private var running = true
    private val username = ledgerEntry.userName

    override fun sendMessage(message: String, messageType: String) {
        if (!this::outputStream.isInitialized) {
            Log.d(TAG, "outputstream is null")
        }
        else {
            try {
                val myUsername = PKIUtils.getUsernameFromCertificate(PKIUtils.getCertificate()!!)
                val messageToSend = NetworkMessage(myUsername, message, messageType).toString()
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
    }

    override fun run() {
        Log.d(TAG, "Starting TLSClient")
        try {
            while(running && !isInterrupted){
                val serverAddress = InetAddress.getByName(ledgerEntry.ipAddress)
                clientSocket = sslContext.socketFactory.createSocket(serverAddress, Constants.TLS_SERVERPORT) as SSLSocket
                outputStream = DataOutputStream(clientSocket.getOutputStream())
                inputStream = DataInputStream(clientSocket.getInputStream())

                sendMessage("", UnicastMessageTypes.CLIENT_HELLO.toString())
                updateAndSendAESKeys()

                while (running && !isInterrupted) {
                    val receivedMessage = inputStream.readUTF()
                    Log.d(TAG, receivedMessage)
                    val networkMessage = NetworkMessage.decodeNetworkMessage(receivedMessage)
                    when(networkMessage.messageType) {
                        UnicastMessageTypes.CLIENT_HELLO.toString() -> {}
                        UnicastMessageTypes.GOODBYE.toString() -> {}
                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                ChatActivity.addChat(networkMessage.sender, networkMessage.payload)
                            }
                        }
                    }

                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAndSendAESKeys() {
        val nextKey = AESUtils.generateAESKey()
        val nextKeyString = AESUtils.keyToString(nextKey)
        AESUtils.setCurrentKeyForUser(username, nextKey)
        sendMessage(nextKeyString, UnicastMessageTypes.KEY_DELIVERY.toString())
        Log.d(TAG, "Encryption key send: $nextKeyString")
    }
}
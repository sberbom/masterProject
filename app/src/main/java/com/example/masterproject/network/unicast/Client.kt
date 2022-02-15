package com.example.masterproject.network.unicast

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.PKIUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey

abstract class Client: Thread() {
    var inputStream: DataInputStream? = null
    var outputStream: DataOutputStream? = null
    private var clientSocket: Socket? = null
    abstract val encryptionKey: SecretKey?
    abstract val ledgerEntry: LedgerEntry
    private var running: Boolean = true
    abstract val port: Int
    abstract val TAG: String

    abstract fun encryptMessageSymmetric(message: String, encryptionKey: SecretKey?): String
    abstract fun decryptMessageSymmetric(message: String, encryptionKey: SecretKey?, ledgerEntry: LedgerEntry): String
    abstract fun createClientSocket(serverAddress: InetAddress): Socket

    override fun run() {
        Log.d(TAG, "Starting $TAG")
        try {
            while(running && !isInterrupted){
                val serverAddress = InetAddress.getByName(ledgerEntry.getIpAddress())
                clientSocket = createClientSocket(serverAddress)
                outputStream = DataOutputStream(clientSocket!!.getOutputStream())
                inputStream = DataInputStream(clientSocket!!.getInputStream())

                sendMessage("", UnicastMessageTypes.CLIENT_HELLO.toString())
                sendKeyMaterial()

                while (running) {
                    val receivedMessage = inputStream!!.readUTF()
                    Log.d(TAG, "Received message: $receivedMessage")
                    val networkMessage = NetworkMessage.decodeNetworkMessage(receivedMessage)
                    val messagePayload= decryptMessageSymmetric(networkMessage.payload, encryptionKey, ledgerEntry)
                    when (networkMessage.messageType) {
                        UnicastMessageTypes.CLIENT_HELLO.toString() -> { }
                        UnicastMessageTypes.GOODBYE.toString() -> { }
                        UnicastMessageTypes.KEY_MATERIAL.toString() -> handleKeyMaterialDelivery(messagePayload)
                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                ChatActivity.addChat(
                                    networkMessage.sender,
                                    messagePayload
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendKeyMaterial() {
        val myKeyMaterial = PKIUtils.generateECKeyPair()
        val keyMaterialString = PKIUtils.encryptionKeyToString(myKeyMaterial.public)
        val receivedKeyMaterial = AESUtils.keyMaterialMap[ledgerEntry.userName]
        if(receivedKeyMaterial != null) {
            val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial.private, receivedKeyMaterial as PublicKey)
            AESUtils.setKeyForUser(ledgerEntry.userName, sharedKey)
            AESUtils.keyMaterialMap.remove(ledgerEntry.userName)
        }
        else{
            AESUtils.keyMaterialMap[ledgerEntry.userName] =
                AESUtils.KeyMaterial(myKeyMaterial.private, true)
        }
        sendMessage(keyMaterialString, UnicastMessageTypes.KEY_MATERIAL.toString())

    }

    private fun handleKeyMaterialDelivery(keyMaterialString: String) {
        val receivedKeyMaterial = PKIUtils.stringToEncryptionKey(keyMaterialString)
        val myKeyMaterial = AESUtils.keyMaterialMap[ledgerEntry.userName]
        if(myKeyMaterial != null) {
            val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial.keyMaterial as PrivateKey, receivedKeyMaterial)
            AESUtils.setKeyForUser(ledgerEntry.userName, sharedKey)
            AESUtils.keyMaterialMap.remove(ledgerEntry.userName)
        }
        else {
            AESUtils.keyMaterialMap[ledgerEntry.userName] =
                AESUtils.KeyMaterial(receivedKeyMaterial, false)
            sendKeyMaterial()
        }
    }

    fun sendMessage(message: String, messageType: String) {
        if (outputStream == null) {
            Log.d(TAG, "outputstream is null")
        } else {
            try {
                val myUsername = PKIUtils.getUsernameFromCertificate(PKIUtils.getStoredCertificate()!!)
                val messageEncrypted = encryptMessageSymmetric(message, encryptionKey)
                val messageToSend = NetworkMessage(myUsername, messageEncrypted, messageType).toString()
                Log.d(TAG, "Message sendt $messageToSend")
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

    open fun closeSocket() {
        if(clientSocket != null) {
            clientSocket!!.close()
        }
    }
}
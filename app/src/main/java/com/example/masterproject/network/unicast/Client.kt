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
    open var ratchet: Ratchet? = null

    abstract fun encryptMessageSymmetric(message: String): String
    abstract fun decryptMessageSymmetric(message: String, ledgerEntry: LedgerEntry, ratchetKey: Int): String
    abstract fun createClientSocket(serverAddress: InetAddress): Socket
    abstract fun getRatchetKeyRound(): Int

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
                    val messagePayload= decryptMessageSymmetric(networkMessage.payload, ledgerEntry, networkMessage.ratchetKey)
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
        val username = ledgerEntry.userName
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

    private fun handleKeyMaterialDelivery(keyMaterialString: String) {
        val username = ledgerEntry.userName
        val receivedKeyMaterial = PKIUtils.stringToEncryptionKey(keyMaterialString)
        val myKeyMaterial = AESUtils.keyMaterialMap[username]?.myPrivateKey
        if(myKeyMaterial != null) {
            val sharedKey = AESUtils.calculateAESKeyDH(myKeyMaterial, receivedKeyMaterial)
            AESUtils.setKeyForUser(username, sharedKey)
            AESUtils.keyMaterialMap[username] = AESUtils.KeyMaterial(null, receivedKeyMaterial)
        }
    }

    fun sendMessage(message: String, messageType: String) {
        if (outputStream == null) {
            Log.d(TAG, "outputstream is null")
        } else {
            try {
                val myUsername = PKIUtils.getUsernameFromCertificate(PKIUtils.getStoredCertificate()!!)
                val ratchetKeyRound = getRatchetKeyRound()
                val messageEncrypted = encryptMessageSymmetric(message)
                val messageToSend = NetworkMessage(myUsername, messageEncrypted, messageType, "", 0, ratchetKeyRound).toString()
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
        ratchet?.clean()
        if(clientSocket != null) {
            clientSocket!!.close()
        }
    }
}
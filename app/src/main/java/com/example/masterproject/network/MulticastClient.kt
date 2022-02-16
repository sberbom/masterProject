package com.example.masterproject.network

import android.content.Context
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.Constants
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MulticastClient (private val server: MulticastServer?) {

    private val TAG = "MulticastClient"
    private val multicastGroup: String = Constants.multicastGroup
    private val multicastPort: Int = Constants.multicastPort
    private val context: Context? = App.getAppContext()

    private fun sendMulticastData(msg: String): Void? {
        val address = InetAddress.getByName(multicastGroup)
        try {
            var serverSocket = DatagramSocket()
            val msgPacket = DatagramPacket(msg.toByteArray(), msg.toByteArray().size, address, multicastPort)
            serverSocket.send(msgPacket)
            Log.d(TAG, "Sent message $msg")
            Thread.sleep(800)
            serverSocket.send(msgPacket)
            serverSocket.close()
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // TODO: The hash of the full ledger should be sent together with users own block
    suspend fun broadcastBlock(nonce: Int) {
        if(context == null) throw Exception("Could not broadcast block, context not defined")
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not broadcast block, private key not defined")
        val block = Ledger.myLedgerEntry ?: throw Exception("No block to broadcast.")
        val signature = PKIUtils.signMessage(block.toString(), privateKey, null)
        val message = NetworkMessage(block.userName, block.toString(), BroadcastMessageTypes.BROADCAST_BLOCK.toString(), signature, nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun requestLedger(nonce: Int) {
        if (server == null) return
        val message = NetworkMessage("", "", BroadcastMessageTypes.REQUEST_LEDGER.toString(), "", nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun sendLedger(nonce: Int) {
        if(context == null) throw Exception("Could not send ledger, context not defined")
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not send ledger, private not defined")
        val certificate = PKIUtils.getStoredCertificate() ?: throw Exception("Could not send ledger, username not defined")
        val username = PKIUtils.getUsernameFromCertificate(certificate)

        val ledger = Ledger.availableDevices.map {it.toString()}.toString()
        val signature = PKIUtils.signMessage(ledger, privateKey, nonce)
        val message = NetworkMessage(username, ledger, BroadcastMessageTypes.FULL_LEDGER.toString(), signature, nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun sendHash(nonce: Int) {
        if(context == null) throw Exception("Could not broadcast block, context not defined")
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not send hash, private not defined")

        val myBlock = Ledger.myLedgerEntry
        val hash = Ledger.getHashOfStoredLedger()
        val signature = PKIUtils.signMessage(hash, privateKey, nonce)
        val message = NetworkMessage(myBlock.toString(), hash, BroadcastMessageTypes.LEDGER_HASH.toString(), signature, nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun requestSpecificHash(hash: String, from: String) {
        val nonce = MISCUtils.generateNonce()
        val message = NetworkMessage("", "$from:$hash", BroadcastMessageTypes.REQUEST_SPECIFIC_LEDGER.toString(), "", nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun sendIpChanged(newIp: String) {
        val payload = MISCUtils.getIpMessageWithTimestamp(newIp)
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not send new ip address, private not defined")
        val signature = PKIUtils.signMessage(payload, privateKey, null)
        val message = NetworkMessage(Ledger.myLedgerEntry.toString(), payload, BroadcastMessageTypes.IP_CHANGED.toString(), signature)
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

}
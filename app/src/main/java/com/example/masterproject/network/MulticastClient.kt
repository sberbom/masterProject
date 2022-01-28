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

class MulticastClient() {

    private val TAG = "MulticastClient"
    private val multicastGroup: String = Constants.multicastGroup
    private val multicastPort: Int = Constants.multicastPort
    private val context: Context? = App.getAppContext()

    private fun sendMulticastData(msg: String): Void? {
        val address = InetAddress.getByName(multicastGroup)
        try {
            val serverSocket = DatagramSocket()
            val msgPacket = DatagramPacket(msg.toByteArray(), msg.toByteArray().size, address, multicastPort)
            serverSocket.send(msgPacket);
            serverSocket.close()
            Log.d(TAG,msg)
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return null;
    }

    // TODO: The hash of the full ledger should be sent together with users own block
    suspend fun broadcastBlock() {
        if(context == null) throw Exception("Could not broadcast block, context not defined")
        val privateKey = PKIUtils.getPrivateKey(context) ?: throw Exception("Could not broadcast block, private not defined")

        val block = Ledger.getMyLedgerEntry().toString()
        val signature = PKIUtils.signMessage(block, privateKey)
        val message = NetworkMessage("", block, BroadcastMessageTypes.BROADCAST_BLOCK.toString(), signature)
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun requestLedger() {
        val message = NetworkMessage("", "", BroadcastMessageTypes.REQUEST_LEDGER.toString())
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun sendLedger() {
        val ledger = Ledger.getFullLedger().map {it.toString()}.toString()
        val message = NetworkMessage("", ledger, BroadcastMessageTypes.FULL_LEDGER.toString())
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

    suspend fun sendHash() {
        val hash = MISCUtils.hashString(Ledger.toString())
        val message = NetworkMessage("", hash, BroadcastMessageTypes.LEDGER_HASH.toString())
        return withContext(Dispatchers.IO) {
            sendMulticastData(message.toString())
        }
    }

}
package com.example.masterproject

import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.Exception
import java.lang.StringBuilder
import java.net.*
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

class MulticastClient() {

    private val TAG = "MulticastClient"
    private val multicastGroup: String = Constants.multicastGroup
    private val multicastPort: Int = Constants.multicastPort

    private fun sendMulticastData(msg: String): Void? {
        var addr = InetAddress.getByName(multicastGroup)
        try {
            var serverSocket = DatagramSocket()
            var msgPacket = DatagramPacket(msg.toByteArray(), msg.toByteArray().size, addr, multicastPort)
            serverSocket.send(msgPacket);
            serverSocket.close()
            Log.d(TAG,msg)
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return null;
    }

    // TODO: The hash of the full ledger should be sent together with users own block
    suspend fun broadcastBlock(): Void? {
        val jsonObject = JSONObject()
        jsonObject.put("type", BroadcastMessageTypes.BROADCAST_BLOCK)
        jsonObject.put("username", Utils.myLedgerEntry!!.userName)
        jsonObject.put("ipAddress", Utils.getMyIpAddress())
        jsonObject.put("certificate", Utils.certificateToString(Utils.myLedgerEntry!!.certificate))
        return withContext(Dispatchers.IO) {
            sendMulticastData(jsonObject.toString())
        }
    }

    suspend fun requestLedger(): Void? {
        val jsonObject = JSONObject()
        jsonObject.put("type", BroadcastMessageTypes.REQUEST_LEDGER)
        return withContext(Dispatchers.IO) {
            sendMulticastData(jsonObject.toString())
        }
    }

    suspend fun sendLedger(): Void? {
        val jsonObject = JSONObject()
        jsonObject.put("type", BroadcastMessageTypes.FULL_LEDGER)
        jsonObject.put("ledger", Ledger.availableDevices.map {it.toString()})
        return withContext(Dispatchers.IO) {
            sendMulticastData(jsonObject.toString())
        }
    }

}
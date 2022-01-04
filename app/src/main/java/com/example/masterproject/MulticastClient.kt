package com.example.masterproject

import android.util.Log
import org.json.JSONObject
import java.lang.Exception
import java.lang.StringBuilder
import java.net.*
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

class MulticastClient(private val multicastGroup: String, private val multicastPort: Int, private val myLedgerEntry: LedgerEntry): Runnable {

    private fun sendMulticastData(): Void? {
        var addr = InetAddress.getByName(multicastGroup)
        try {
            var serverSocket = DatagramSocket()
            var msg = createMulticastMessage()
            var msgPacket = DatagramPacket(msg.toByteArray(), msg.toByteArray().size, addr, multicastPort)
            serverSocket.send(msgPacket);
            serverSocket.close()
            //Log.d("DEBUG SIGMUND","Multicast packet sendt")
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return null;
    }

    private fun createMulticastMessage(): String {
        val jsonObject = JSONObject()
        jsonObject.put("username", myLedgerEntry.userName)
        jsonObject.put("ipAddress", Utils.getMyIpAddress())
        jsonObject.put("certificate", Utils.certificateToString(myLedgerEntry.certificate))
        return jsonObject.toString()
    }

    override fun run() {
        try{
            while (true){
                sendMulticastData()
                Thread.sleep(2000)
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }



}
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
        jsonObject.put("ipAddress", getMyIpAddress())
        jsonObject.put("publicKey", keyToString(myLedgerEntry.publicKey))
        return jsonObject.toString()
    }

    private fun keyToString(key: PublicKey): String {
        return Base64.getEncoder().encodeToString(key.encoded)
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


    fun getMyIpAddress(): String? {
        try {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val networkInterface: NetworkInterface = en.nextElement()
                val enumIpAddress: Enumeration<InetAddress> = networkInterface.inetAddresses
                while (enumIpAddress.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddress.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        }
        catch (e:Exception) {
            e.printStackTrace()
        }
        return null
    }
}
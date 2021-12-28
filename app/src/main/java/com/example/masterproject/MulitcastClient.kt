package com.example.masterproject

import android.util.Log
import java.lang.Exception
import java.net.*
import java.util.*

class MulitcastClient(multicastGroup: String, multicastPort: Int): Runnable {

    var multicastPort: Int = multicastPort;
    var multicastGroup: String = multicastGroup;

    fun sendMulticastData(): Void? {
        var addr = InetAddress.getByName(multicastGroup)
        try {
            var serverSocket = DatagramSocket()
            var msg = "${getMyIpAddress()}"
            var msgPacket = DatagramPacket(msg.toByteArray(), msg.toByteArray().size, addr, multicastPort)
            serverSocket.send(msgPacket);
            //Log.d("DEBUG SIGMUND","Multicast packet sendt")
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return null;
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
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
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
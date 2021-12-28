package com.example.masterproject

import android.util.Log
import java.lang.Exception
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket


class MulticastServer(multicastGroup: String, multicastPort: Int): Runnable {

    private var multicastPort: Int = multicastPort;
    private var multicastGroup: String = multicastGroup;
    var availableDevices: MutableList<String> = mutableListOf();

    fun listenForDevices(): MutableList<String>? {
        var addr = InetAddress.getByName(multicastGroup);
        var buf = ByteArray(256)
        try{
            var clientSocket = MulticastSocket(multicastPort);
            clientSocket.joinGroup(addr)

            while (true) {
                var msgPacket = DatagramPacket(buf, buf.size)
                clientSocket.receive(msgPacket);

                var msgRaw = String(buf, 0, buf.size);

                val regex = Regex("[^A-Za-z0-9.]")
                var msg = regex.replace(msgRaw, "");
                //Log.d("DEBUG SIGMUND", msg)

                if(!availableDevices.contains(msg)){
                    availableDevices.add(msg)
                }
            }
        }catch (e: Exception){
            e.printStackTrace();
        }
        return null;
    }

    override fun run() {
        listenForDevices()
    }
}
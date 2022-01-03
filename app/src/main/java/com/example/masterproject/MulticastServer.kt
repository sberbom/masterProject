package com.example.masterproject

import android.util.Log
import org.json.JSONObject
import java.lang.Exception
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.security.Key
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*


class MulticastServer(private val multicastGroup: String, private val multicastPort: Int): Runnable {

    private fun listenForDevices(): MutableList<LedgerEntry>? {
        val address = InetAddress.getByName(multicastGroup);
        val buf = ByteArray(256)
        //Log.d("SIGMUND", multicastGroup)
        try{
            val clientSocket = MulticastSocket(multicastPort);
            clientSocket.joinGroup(address)

            while (true) {
                val msgPacket = DatagramPacket(buf, buf.size)
                clientSocket.receive(msgPacket);

                val msgRaw = String(buf, 0, buf.size);

                //val regex = Regex("[^A-Za-z0-9.]")
                //val msg = regex.replace(msgRaw, "");

                //Log.d("SIGMUND", msgRaw)

                val jsonObject = JSONObject(msgRaw)
                val username = jsonObject.getString("username")
                val publicKeyString = jsonObject.getString("publicKey")
                val ipAddress = jsonObject.getString("ipAddress")
                val ledgerEntry = LedgerEntry(stringToKey(publicKeyString), username, ipAddress)


                if(isAddEntryToLedger(ledgerEntry)){
                    Ledger.availableDevices.add(ledgerEntry)
                }
            }
        }catch (e: Exception){
            e.printStackTrace();
        }
        return null;
    }

    private fun isAddEntryToLedger(ledgerEntry: LedgerEntry): Boolean {
        //check on both username and ip. Can remove ip when proper user registration
        val users = mutableListOf<String>()
        val ips = mutableListOf<String>()
        for(device in Ledger.availableDevices){
            users.add(device.userName)
            ips.add(device.ipAddress)
        }
        return !users.contains(ledgerEntry.userName) && !ips.contains(ledgerEntry.ipAddress)
    }

    private fun stringToKey(key: String): PublicKey {
        val encKey = Base64.getDecoder().decode(key)
        val publicKeySpec = X509EncodedKeySpec(encKey)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(publicKeySpec)
    }

    override fun run() {
        listenForDevices()
    }
}
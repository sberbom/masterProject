package com.example.masterproject

import android.util.Log
import org.json.JSONObject
import java.lang.Exception
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket


class MulticastServer(private val multicastGroup: String, private val multicastPort: Int): Runnable {

    private val TAG = "MutlicastServer"

    private fun listenForDevices(): MutableList<LedgerEntry>? {
        val address = InetAddress.getByName(multicastGroup);
        val buf = ByteArray(512)
        try{
            val clientSocket = MulticastSocket(multicastPort);
            clientSocket.joinGroup(address)

            while (true) {
                val msgPacket = DatagramPacket(buf, buf.size)
                clientSocket.receive(msgPacket);

                val msgRaw = String(buf, 0, buf.size)
                Log.d(TAG, msgRaw)
                val jsonObject = JSONObject(msgRaw)
                if (jsonObject.getString("type") == BroadcastMessageTypes.BROADCAST_LEDGER.toString()) {
                    val username = jsonObject.getString("username")
                    val certificateString = jsonObject.getString("certificate")
                    val ipAddress = jsonObject.getString("ipAddress")
                    val ledgerEntry = LedgerEntry(Utils.stringToCertificate(certificateString), username, ipAddress)

                    if(isAddEntryToLedger(ledgerEntry)){
                        Ledger.availableDevices.add(ledgerEntry)
                    }
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
        for(ledgerEntry in Ledger.availableDevices){
            users.add(ledgerEntry.userName)
        }
        return !users.contains(ledgerEntry.userName)
    }

    override fun run() {
        listenForDevices()
    }
}
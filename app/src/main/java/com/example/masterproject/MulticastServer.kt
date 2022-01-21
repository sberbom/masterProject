package com.example.masterproject

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.Exception
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import com.example.masterproject.Ledger.Companion.availableDevices



class MulticastServer(private val multicastGroup: String, private val multicastPort: Int): Runnable {

    private val TAG = "MutlicastServer"

    private fun listenForDevices(): MutableList<LedgerEntry>? {
        val address = InetAddress.getByName(multicastGroup);
        val buf = ByteArray(2048)
        try{
            val clientSocket = MulticastSocket(multicastPort);
            clientSocket.joinGroup(address)

            while (true) {
                val msgPacket = DatagramPacket(buf, buf.size)
                clientSocket.receive(msgPacket)

                val msgRaw = String(buf, 0, buf.size)
                Log.d(TAG, "msgRaw $msgRaw")
                val jsonObject = JSONObject(msgRaw)
                Log.d(TAG, "object ${jsonObject.toString()}")
                when (jsonObject.getString("type")) {
                    BroadcastMessageTypes.BROADCAST_BLOCK.toString() -> handleBroadcastedBlock(jsonObject)
                    BroadcastMessageTypes.REQUEST_LEDGER.toString() -> handleRequestedLedger(jsonObject)
                    BroadcastMessageTypes.FULL_LEDGER.toString() -> handleFullLedger(jsonObject)
                }
            }
        }catch (e: Exception){
            e.printStackTrace();
        }
        return null;
    }

    private fun handleBroadcastedBlock(jsonObject: JSONObject) {
        val username = jsonObject.getString("username")
        val certificateString = jsonObject.getString("certificate")
        val ipAddress = jsonObject.getString("ipAddress")
        val ledgerEntry = LedgerEntry(Utils.stringToCertificate(certificateString), username, ipAddress)

        if(isAddEntryToLedger(ledgerEntry)){
            Ledger.availableDevices.add(ledgerEntry)
        }
    }

    private fun handleRequestedLedger(jsonObject: JSONObject) {
        val multicastClient = MulticastClient(multicastGroup, multicastPort)
        GlobalScope.launch (Dispatchers.IO) {
            multicastClient.sendLedger()
        }
    }

    private fun handleFullLedger(jsonObject: JSONObject) {
        val ledger = jsonObject.getString("ledger")
        val ledgerWithoutBrackets = ledger.substring(1, ledger.length - 1)
        if (ledgerWithoutBrackets.isNotEmpty()) {
            // split between array objects
            val ledgerArray = ledgerWithoutBrackets.split("},{")
            ledgerArray.forEach{
                val ledgerEntry = LedgerEntry.parseString(it)
                if (isAddEntryToLedger(ledgerEntry)) availableDevices.add(ledgerEntry)
            }
        }
    }

    private fun isAddEntryToLedger(ledgerEntry: LedgerEntry): Boolean {
        //check on both username and ip. Can remove ip when proper user registration
        val users = availableDevices.map { it.userName }
        return !users.contains(ledgerEntry.userName)
    }

    override fun run() {
        listenForDevices()
    }
}
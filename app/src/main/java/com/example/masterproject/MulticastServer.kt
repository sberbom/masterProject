package com.example.masterproject

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.Exception
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MulticastServer: Service() {

    private val TAG = "MulticastServer"
    private val socket: MulticastSocket? = null
    private val address = InetAddress.getByName(Constants.multicastGroup);

    private val registrationHandler: RegistrationHandler = RegistrationHandler(MulticastServer@this)
    private val client: MulticastClient = MulticastClient()

    private fun listenForData(): MutableList<LedgerEntry>? {
        val buf = ByteArray(2048)
        try{
            val socket = MulticastSocket(Constants.multicastPort);
            socket.joinGroup(address)
            Log.d(TAG, "Listening for data.")
            while (true) {
                val msgPacket = DatagramPacket(buf, buf.size)
                socket.receive(msgPacket)

                val msgRaw = String(buf, 0, buf.size)
                val jsonObject = JSONObject(msgRaw)
                Log.d(TAG, "object ${jsonObject.toString()}")
                when (jsonObject.getString("type")) {
                    BroadcastMessageTypes.BROADCAST_BLOCK.toString() -> handleBroadcastBlock(jsonObject)
                    BroadcastMessageTypes.REQUEST_LEDGER.toString() -> handleRequestedLedger(jsonObject)
                    BroadcastMessageTypes.FULL_LEDGER.toString() -> handleFullLedger(jsonObject)
                }
            }
        }catch (e: Exception){
            e.printStackTrace();
        }
        return null;
    }

    private fun handleBroadcastBlock(jsonObject: JSONObject) {
        val username = jsonObject.getString("username")
        val certificateString = jsonObject.getString("certificate")
        val ipAddress = jsonObject.getString("ipAddress")
        val ledgerEntry = LedgerEntry(Utils.stringToCertificate(certificateString), username, ipAddress)
        Ledger.addLedgerEntry(ledgerEntry)
    }

    private fun handleRequestedLedger(jsonObject: JSONObject) {
        GlobalScope.launch (Dispatchers.IO) {
            client.sendLedger()
        }
    }

    private fun handleFullLedger(jsonObject: JSONObject) {
        val ledger = jsonObject.getString("ledger")
        val ledgerWithoutBrackets = ledger.substring(1, ledger.length - 1)
        if (ledgerWithoutBrackets.isNotEmpty()) {
            // split between array objects
            val ledgerArray = ledgerWithoutBrackets.split("},{")
            val fullLedger: List<LedgerEntry> = ledgerArray.map{ LedgerEntry.parseString(it)}
            registrationHandler.fullLedgerReceived(fullLedger)
            fullLedger.forEach{
                Ledger.addLedgerEntry(it)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Service started")
        GlobalScope.launch {
            listenForData()
        }
        GlobalScope.launch {
            //client.broadcastBlock()
            client.requestLedger()
            registrationHandler.startWaitForLedgerTimer()
        }
    }

    override fun onDestroy() {
        if (socket != null) {
            socket.leaveGroup(address)
            socket.close()
        }
        super.onDestroy()

    }
}
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
                when (jsonObject.getString("type")) {
                    BroadcastMessageTypes.BROADCAST_BLOCK.toString() -> handleBroadcastBlock(jsonObject)
                    BroadcastMessageTypes.REQUEST_LEDGER.toString() -> handleRequestedLedger()
                    BroadcastMessageTypes.FULL_LEDGER.toString() -> handleFullLedger(jsonObject)
                    BroadcastMessageTypes.LEDGER_HASH.toString() -> handleHash(jsonObject)
                }
            }
        }catch (e: Exception){
            e.printStackTrace();
        }
        return null;
    }

    private fun handleBroadcastBlock(jsonObject: JSONObject) {
        val blockString = jsonObject.getString("block")
        val block = LedgerEntry.parseString(blockString)
        Log.d(TAG, "Received broadcast block: $block")
        Ledger.addLedgerEntry(block)
    }

    private fun handleRequestedLedger() {
        Log.d(TAG, "Received request for ledger.")
        if (Ledger.getFullLedger().isNotEmpty()) {
            GlobalScope.launch (Dispatchers.IO) {
                val shouldSendFullLedger = Ledger.shouldSendFullLedger()
                Log.d(TAG, "Should send full ledger: $shouldSendFullLedger")
                if (shouldSendFullLedger) {
                    client.sendLedger()
                } else {
                    client.sendHash()
                }
            }
        }
    }

    private fun handleFullLedger(jsonObject: JSONObject) {
        val ledger = jsonObject.getString("ledger")
        Log.d(TAG, "Received full ledger: $ledger")
        val ledgerWithoutBrackets = ledger.substring(1, ledger.length - 1)
        if (ledgerWithoutBrackets.isNotEmpty()) {
            // split between array objects
            val ledgerArray = ledgerWithoutBrackets.split(", ")
            val fullLedger: List<LedgerEntry> = ledgerArray.map{ LedgerEntry.parseString(it)}
            registrationHandler.fullLedgerReceived(fullLedger)
        }
    }

    private fun handleHash(jsonObject: JSONObject) {
        val hash = jsonObject.getString("hash")
        Log.d(TAG, "Received hash: $hash")
        registrationHandler.hashOfLedgerReceived(hash)
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
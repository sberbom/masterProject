package com.example.masterproject.network

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.ledger.ReceivedHash
import com.example.masterproject.ledger.RegistrationHandler
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.Constants
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MulticastServer: Service() {

    private val TAG = "MulticastServer"
    private val socket: MulticastSocket? = null
    private val address = InetAddress.getByName(Constants.multicastGroup);

    private val registrationHandler: RegistrationHandler = RegistrationHandler()
    private val client: MulticastClient = MulticastClient()

    private fun listenForData(): MutableList<LedgerEntry>? {
        val buf = ByteArray(512 * 10)
        try{
            val socket = MulticastSocket(Constants.multicastPort);
            socket.joinGroup(address)
            Log.d(TAG, "Listening for data.")
            while (true) {
                val msgPacket = DatagramPacket(buf, buf.size)
                socket.receive(msgPacket)

                val msgRaw = String(buf, 0, buf.size)
                val networkMessage = NetworkMessage.decodeNetworkMessage(msgRaw)
                Log.d(TAG, "MESSAGE RECEIVED: $networkMessage")
                when (networkMessage.messageType) {
                    BroadcastMessageTypes.BROADCAST_BLOCK.toString() -> handleBroadcastBlock(networkMessage)
                    BroadcastMessageTypes.REQUEST_LEDGER.toString() -> handleRequestedLedger()
                    BroadcastMessageTypes.FULL_LEDGER.toString() -> handleFullLedger(networkMessage)
                    BroadcastMessageTypes.LEDGER_HASH.toString() -> handleHash(networkMessage)
                }
            }
        }catch (e: Exception){
            e.printStackTrace();
        }
        return null;
    }

    private fun handleBroadcastBlock(networkMessage: NetworkMessage) {
        val blockString = networkMessage.payload
        val block = LedgerEntry.parseString(blockString)
        val publicKey = block.certificate.publicKey
        val isValidSignature = PKIUtils.verifySignature(blockString, networkMessage.signature, publicKey)
        if(isValidSignature) {
            Ledger.addLedgerEntry(block)
        }
        else {
            Log.d(TAG, "Could not add block, signature not valid")
        }
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

    private fun handleFullLedger(networkMessage: NetworkMessage) {
        val ledger = networkMessage.payload
        val publicKey = Ledger.getLedgerEntry(networkMessage.sender)?.certificate?.publicKey ?: throw Exception("Can not handle full ledger - Could not find public key for user")
        val isValidSignature = PKIUtils.verifySignature(ledger, networkMessage.signature, publicKey)
        if(isValidSignature) {
            Log.d(TAG, "Received full ledger: $ledger")
            val ledgerWithoutBrackets = ledger.substring(1, ledger.length - 1)
            if (ledgerWithoutBrackets.isNotEmpty()) {
                // split between array objects
                val ledgerArray = ledgerWithoutBrackets.split(", ")
                val fullLedger: List<LedgerEntry> = ledgerArray.map{ LedgerEntry.parseString(it)}
                registrationHandler.fullLedgerReceived(fullLedger)
            }
        }
        else {
            Log.d(TAG, "Can not handle full ledger, signature not valid")
        }
    }

    private fun handleHash(networkMessage: NetworkMessage) {
        val receivedHash = ReceivedHash(networkMessage.payload, networkMessage.signature, LedgerEntry.parseString(networkMessage.sender))
        val publicKey = receivedHash.senderBlock.certificate.publicKey ?: throw Exception("Can not handle full ledger - Could not find public key for user")
        val isValidSignature = PKIUtils.verifySignature(receivedHash.hash, receivedHash.signature, publicKey)
        if (isValidSignature) {
            Log.d(TAG, "Received hash from ${receivedHash.senderBlock.userName}: ${receivedHash.hash}")
            registrationHandler.hashOfLedgerReceived(receivedHash)
        } else {
            Log.d(TAG, "Hash from ${receivedHash.senderBlock.userName} rejected, signature not valid.")
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
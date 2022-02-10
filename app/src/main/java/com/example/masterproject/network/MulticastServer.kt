package com.example.masterproject.network

import android.app.Service
import android.app.appsearch.GlobalSearchSession
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.ledger.RegistrationHandler
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.Constants
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.*
import kotlin.concurrent.schedule

class MulticastServer: Service() {

    private val TAG = "MulticastServer"
    private val socket: MulticastSocket? = null
    private val address = InetAddress.getByName(Constants.multicastGroup)

    private val antiDosTimer = Timer()
    private var shouldHandleRequests = true

    private val registrationHandlers: MutableMap<Int, RegistrationHandler> = mutableMapOf()
    private val finishedRegistrationProcesses: MutableList<Int> = mutableListOf()
    private val client: MulticastClient = MulticastClient(this)

    private val usedNonces: MutableList<Int> = mutableListOf()

    private fun listenForData(): MutableList<LedgerEntry>? {
        val buf = ByteArray(512 * 12)
        try{
            val socket = MulticastSocket(Constants.multicastPort)
            socket.joinGroup(address)
            Log.d(TAG, "Listening for data.")
            while (true) {
                val msgPacket = DatagramPacket(buf, buf.size)
                socket.receive(msgPacket)

                val msgRaw = String(buf, 0, buf.size)
                val networkMessage = NetworkMessage.decodeNetworkMessage(msgRaw)
                Log.d(TAG, networkMessage.toString())
                GlobalScope.launch(Dispatchers.IO) {
                    when (networkMessage.messageType) {
                        BroadcastMessageTypes.BROADCAST_BLOCK.toString() -> handleBroadcastBlock(networkMessage)
                        BroadcastMessageTypes.REQUEST_LEDGER.toString() -> handleRequestedLedger(networkMessage)
                        BroadcastMessageTypes.REQUEST_SPECIFIC_LEDGER.toString() -> handleSpecificLedgerRequest(networkMessage)
                        BroadcastMessageTypes.FULL_LEDGER.toString() -> handleFullLedger(networkMessage)
                        BroadcastMessageTypes.LEDGER_HASH.toString() -> handleHash(networkMessage)
                        else -> Log.d(TAG, "Received unknown message type.")
                    }
                }
            }
        }catch (e: Exception){
            e.printStackTrace()
        }
        return null
    }

    private fun handleBroadcastBlock(networkMessage: NetworkMessage) {
        if (networkMessage.sender == Ledger.getMyLedgerEntry()?.userName) return
        Log.d(TAG, "Broadcast block received ${networkMessage.payload}")
        val blockString = networkMessage.payload
        val block = LedgerEntry.parseString(blockString)
        val publicKey = block.certificate.publicKey
        val isValidSignature = PKIUtils.verifySignature(blockString, networkMessage.signature, publicKey, null)
        Log.d(TAG, "Signature is valid: $isValidSignature")
        if(isValidSignature) {
            Ledger.addLedgerEntry(block)
        }
        else {
            Log.d(TAG, "Could not add block, signature not valid")
        }
    }

    // TODO: Should not send hash if there are CA-certified and you are not one of them
    private fun handleRequestedLedger(networkMessage: NetworkMessage) {
        if (!shouldHandleRequests) return
        val registrationHandler = startRegistrationProcess(networkMessage.nonce, false) ?: return
        Log.d(TAG, "Received request for ledger with nonce: ${networkMessage.nonce}.")
        // must be a copy of the real list
        val fullLedger = Ledger.getFullLedger().toList()
        val myBlock = Ledger.getMyLedgerEntry()
        if (fullLedger.isNotEmpty() && myBlock != null) {
            GlobalScope.launch (Dispatchers.IO) {
                if (Ledger.shouldSendFullLedger()) {
                    // register your own ledger as a vote in your own registration process
                    registrationHandler.fullLedgerReceived(myBlock, fullLedger)
                    client.sendLedger(networkMessage.nonce)
                } else {
                    // register your own hash as a vote in your own registration process
                    registrationHandler.hashOfLedgerReceived(myBlock, Ledger.getHashOfStoredLedger())
                    client.sendHash(networkMessage.nonce)
                }
            }
        }
        shouldHandleRequests = false
        antiDosTimer.schedule(500) {
            shouldHandleRequests = true
        }
    }

    private fun handleSpecificLedgerRequest(networkMessage: NetworkMessage) {
        val payloadArray = networkMessage.payload.split(":")
        if (payloadArray.size > 1) {
            val usernameToReply = payloadArray[0]
            val hash = payloadArray[1]
            Log.d(TAG, "Received request for $usernameToReply to send ledger with hash $hash")
            if (usernameToReply == Ledger.getMyLedgerEntry()?.userName && Ledger.getHashOfStoredLedger() == hash) {
                GlobalScope.launch(Dispatchers.IO) {
                    client.sendLedger(networkMessage.nonce)
                }
            }
        } else {
            Log.d(TAG, "Message received was wrong format.")
        }
    }

    private fun handleFullLedger(networkMessage: NetworkMessage) {
        if (networkMessage.sender == Ledger.getMyLedgerEntry()?.userName) return
        val registrationHandler = startRegistrationProcess(networkMessage.nonce, false) ?: return
        val ledger = networkMessage.payload
        Log.d(TAG, "Received full ledger from ${networkMessage.sender}: $ledger")
        val ledgerWithoutBrackets = ledger.substring(1, ledger.length - 1)
        if (ledgerWithoutBrackets.isNotEmpty()) {
            // split between array objects
            val ledgerArray = ledgerWithoutBrackets.split(", ")
            val fullLedger: List<LedgerEntry> = ledgerArray.map{ LedgerEntry.parseString(it)}
            val usersWithUsernameOfSender = fullLedger.filter { it.userName == networkMessage.sender }
            if (usersWithUsernameOfSender.isEmpty()) throw Exception("Sender is not in ledger.")
            val blockOfSender = if (usersWithUsernameOfSender.size == 1) usersWithUsernameOfSender[0] else usersWithUsernameOfSender[1]
            val publicKey = blockOfSender.certificate.publicKey
            val isValidSignature = PKIUtils.verifySignature(ledger, networkMessage.signature, publicKey, networkMessage.nonce)
            if(isValidSignature) {
                registrationHandler.fullLedgerReceived(blockOfSender, fullLedger)
            } else {
                Log.d(TAG, "Can not handle full ledger, signature not valid")
            }
        }
    }

    private fun handleHash(networkMessage: NetworkMessage) {
        val registrationHandler = startRegistrationProcess(networkMessage.nonce, false) ?: return
        val senderBlock = LedgerEntry.parseString(networkMessage.sender)
        if (senderBlock.userName == Ledger.getMyLedgerEntry()?.userName) return
        Log.d(TAG, "Received hash from ${senderBlock.userName}: ${networkMessage.payload}")
        val publicKey = senderBlock.certificate.publicKey ?: throw Exception("Can not handle full ledger - Could not find public key for user")
        val isValidSignature = PKIUtils.verifySignature(networkMessage.payload, networkMessage.signature, publicKey, networkMessage.nonce)
        if (isValidSignature) {
            registrationHandler.hashOfLedgerReceived(senderBlock, networkMessage.payload)
        }
    }

    private fun startRegistrationProcess(nonce: Int, isMyRegistration: Boolean): RegistrationHandler? {
        if(!finishedRegistrationProcesses.contains(nonce)) {
            val existingRegistrationHandler = registrationHandlers[nonce]
            if (existingRegistrationHandler != null) return existingRegistrationHandler
            Log.d(TAG, "Registration process started with nonce: $nonce")
            val registrationHandler = RegistrationHandler(this, nonce, isMyRegistration)
            registrationHandlers[nonce] = registrationHandler
            registrationHandler.startTimers()
            return registrationHandler
        }
        return null
    }

    fun registrationProcessFinished(nonce: Int) {
        if (registrationHandlers[nonce] != null) {
            Log.d(TAG, "Registration process finished: $nonce")
            finishedRegistrationProcesses.add(nonce)
            registrationHandlers.remove(nonce)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Service started")
        val nonce = MISCUtils.generateNonce()
        startRegistrationProcess(nonce, true)
        GlobalScope.launch {
            listenForData()
        }
        GlobalScope.launch {
            client.requestLedger(nonce)
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
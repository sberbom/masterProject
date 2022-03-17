package com.example.masterproject.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.util.Log
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.ledger.ReceivedLedger
import com.example.masterproject.ledger.RegistrationHandler
import com.example.masterproject.types.MulticastPacket
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

    private var currentNetwork: Network? = null

    private fun listenForData(): MutableList<LedgerEntry>? {
        val buf = ByteArray(512 * 3)
        try{
            val socket = MulticastSocket(Constants.multicastPort)
            socket.joinGroup(address)
            Log.d(TAG, "MULTICAST_SERVER_LISTENING")
            while (true) {
                try {
                    val msgPacket = DatagramPacket(buf, buf.size)
                    socket.receive(msgPacket)

                    val msgRaw = String(buf, 0, buf.size)
                    val multicastPacket = MulticastPacket.decodeMulticastPacket(msgRaw)
                    GlobalScope.launch(Dispatchers.IO) {
                            when (multicastPacket.messageType) {
                                BroadcastMessageTypes.BROADCAST_BLOCK.toString() -> handleBroadcastBlock(multicastPacket)
                                BroadcastMessageTypes.REQUEST_LEDGER.toString() -> handleRequestedLedger(multicastPacket)
                                BroadcastMessageTypes.REQUEST_SPECIFIC_LEDGER.toString() -> handleSpecificLedgerRequest(multicastPacket)
                                BroadcastMessageTypes.FULL_LEDGER.toString() -> handleFullLedger(multicastPacket)
                                BroadcastMessageTypes.LEDGER_HASH.toString() -> handleHash(multicastPacket)
                                BroadcastMessageTypes.IP_CHANGED.toString() -> handleIpChanged(multicastPacket)
                                else -> Log.d(TAG, "Received unknown message type.")
                            }

                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }catch (e: Exception){
            e.printStackTrace()
        }
        return null
    }

    private fun handleBroadcastBlock(multicastPacket: MulticastPacket) {
        val blockString = multicastPacket.payload
        val block = LedgerEntry.parseString(blockString)
        val myLedgerEntry = Ledger.myLedgerEntry
        if (myLedgerEntry != null && PKIUtils.certificateToString(block.certificate) == PKIUtils.certificateToString(myLedgerEntry.certificate)) return
        Log.d(TAG, "Broadcast block received ${multicastPacket.payload}")
        val publicKey = block.certificate.publicKey
        val isValidSignature = PKIUtils.verifySignature(blockString, multicastPacket.signature, publicKey, null)
        if(isValidSignature) {
            Ledger.addLedgerEntry(block)
        }
    }

    private fun handleRequestedLedger(multicastPacket: MulticastPacket) {
        if (!shouldHandleRequests) return
        val registrationHandler = startRegistrationProcess(multicastPacket.nonce, false) ?: return
        // if I started the registration, I will not send anything
        Log.d(TAG, "Received request for ledger with nonce: ${multicastPacket.nonce}.")
        // must be a copy of the real list
        val fullLedger = Ledger.availableDevices.toList()
        Log.d(TAG, "$fullLedger")
        val myBlock = Ledger.myLedgerEntry
        Log.d(TAG, "$myBlock")
        if (fullLedger.isNotEmpty() && myBlock != null) {
            GlobalScope.launch (Dispatchers.IO) {
                if (Ledger.shouldSendFullLedger()) {
                    // register your own ledger as a vote in your own registration process
                    registrationHandler.handleSendLedger(ReceivedLedger(fullLedger, Ledger.getHashOfLedger(fullLedger) , myBlock))
                    MulticastClient.sendLedger(multicastPacket.nonce)
                } else {
                    // register your own hash as a vote in your own registration process
                    registrationHandler.hashOfLedgerReceived(myBlock, Ledger.getHashOfStoredLedger())
                    MulticastClient.sendHash(multicastPacket.nonce)
                }
            }
        }
        shouldHandleRequests = false
        antiDosTimer.schedule(500) {
            shouldHandleRequests = true
        }
    }

    private fun handleSpecificLedgerRequest(multicastPacket: MulticastPacket) {
        val payloadArray = multicastPacket.payload.split(":")
        if (payloadArray.size > 1) {
            if (registrationHandlers[multicastPacket.nonce] == null) return
            val usernameToReply = payloadArray[0]
            val hash = payloadArray[1]
            Log.d(TAG, "Received request for $usernameToReply to send ledger with hash $hash")
            if (usernameToReply == Ledger.myLedgerEntry?.userName && Ledger.getHashOfStoredLedger() == hash) {
                GlobalScope.launch(Dispatchers.IO) {
                    MulticastClient.sendLedger(multicastPacket.nonce)
                }
            }
        }
    }

    private fun handleFullLedger(multicastPacket: MulticastPacket) {
        if ((multicastPacket.sequenceNumber == 0 && multicastPacket.sender == Ledger.myLedgerEntry?.toString()) ||
            (multicastPacket.sequenceNumber > 0 && multicastPacket.sender == Ledger.myLedgerEntry?.userName)) return
        val registrationHandler = registrationHandlers[multicastPacket.nonce] ?: return
        registrationHandler.fullLedgerReceived(multicastPacket)
    }

    private fun handleHash(multicastPacket: MulticastPacket) {
        val registrationHandler = registrationHandlers[multicastPacket.nonce] ?: return
        val senderBlock = LedgerEntry.parseString(multicastPacket.sender)
        if (senderBlock.userName == Ledger.myLedgerEntry?.userName) return
        Log.d(TAG, "Received hash from ${senderBlock.userName}: ${multicastPacket.payload}")
        val publicKey = senderBlock.certificate.publicKey ?: throw Exception("Can not handle full ledger - Could not find public key for user")
        val isValidSignature = PKIUtils.verifySignature(multicastPacket.payload, multicastPacket.signature, publicKey, multicastPacket.nonce)
        if (isValidSignature) {
            registrationHandler.hashOfLedgerReceived(senderBlock, multicastPacket.payload)
        }
    }

    private fun handleIpChanged(multicastPacket: MulticastPacket) {
        val senderBlock = LedgerEntry.parseString(multicastPacket.sender)
        if (senderBlock.userName == Ledger.myLedgerEntry?.userName) return
        val publicKey = senderBlock.certificate.publicKey ?: throw Exception("Can not handle ip changed - Could not find public key for user")
        val blockToChange = Ledger.getLedgerEntry(senderBlock.userName) ?: return
        val newIp = multicastPacket.payload.split(":").first()
        val senderHasCorrectCertificate = PKIUtils.certificateToString(blockToChange.certificate) == PKIUtils.certificateToString(senderBlock.certificate)
        val isValidSignature = PKIUtils.verifySignature(multicastPacket.payload, multicastPacket.signature, publicKey, null)
        val isValidTimestamp = isValidTimestampFromIpMessage(multicastPacket.payload)
        if (senderHasCorrectCertificate && newIp != blockToChange.getIpAddress() && isValidSignature && isValidTimestamp) {
            blockToChange.setIpAddress(newIp)
        }
    }

    private fun isValidTimestampFromIpMessage (payload: String): Boolean {
        return try {
            val timestampFromMessage = payload.split(":")[1].toLong()
            val now = System.currentTimeMillis()
            // timestamp must be from between 1s back in time and 0.5s forward in time.
            (now - 1000 < timestampFromMessage) && (now + 500 > timestampFromMessage)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun startRegistrationProcess(nonce: Int, isMyRegistration: Boolean): RegistrationHandler? {
        if (!finishedRegistrationProcesses.contains(nonce) && registrationHandlers[nonce] == null) {
            val registrationHandler = RegistrationHandler(this, nonce, isMyRegistration)
            registrationHandler.startTimers()
            registrationHandlers[nonce] = registrationHandler
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
        GlobalScope.launch {
            listenForData()
        }
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connMgr.registerNetworkCallback(NetworkRequest.Builder().build(), NetworkCallbackImp(this))
    }

    override fun onDestroy() {
        if (socket != null) {
            socket.leaveGroup(address)
            socket.close()
        }
        MulticastClient.closeSocket()
        super.onDestroy()

    }

    internal class NetworkCallbackImp(private val server: MulticastServer) : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            if (server.currentNetwork != network) {
                // When joining a new network, information from the previous network should be reset
                server.registrationHandlers.clear()
                server.finishedRegistrationProcesses.clear()
                server.currentNetwork = network
                Ledger.clearLedger()
                val nonce = MISCUtils.generateNonce()
                if (isWifi) {
                    server.startRegistrationProcess(nonce, true)
                    GlobalScope.launch(Dispatchers.IO) {
                        MulticastClient.requestLedger(nonce)
                    }
                }
            }
        }
    }
}
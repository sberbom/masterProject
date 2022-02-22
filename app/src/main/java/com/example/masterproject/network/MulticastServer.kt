package com.example.masterproject.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.*
import android.os.IBinder
import android.util.Log
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.ledger.ReceivedLedger
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
import java.security.cert.X509Certificate
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

    private val certificateStringToSenderBlock: MutableMap<String, LedgerEntry> = mutableMapOf()

    private val usernameToCertificates: MutableMap<String, MutableList<X509Certificate>> = mutableMapOf()

    private val ledgerFragmentsReceived: MutableMap<String, MutableList<String?>> = mutableMapOf()

    private val orphanFragment: MutableMap<String, MutableList<NetworkMessage>> = mutableMapOf()

    private var currentNetwork: Network? = null

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
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        when (networkMessage.messageType) {
                            BroadcastMessageTypes.BROADCAST_BLOCK.toString() -> handleBroadcastBlock(networkMessage)
                            BroadcastMessageTypes.REQUEST_LEDGER.toString() -> handleRequestedLedger(networkMessage)
                            BroadcastMessageTypes.REQUEST_SPECIFIC_LEDGER.toString() -> handleSpecificLedgerRequest(networkMessage)
                            BroadcastMessageTypes.FULL_LEDGER.toString() -> handleFullLedger(networkMessage)
                            BroadcastMessageTypes.LEDGER_HASH.toString() -> handleHash(networkMessage)
                            BroadcastMessageTypes.IP_CHANGED.toString() -> handleIpChanged(networkMessage)
                            else -> Log.d(TAG, "Received unknown message type.")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }catch (e: Exception){
            e.printStackTrace()
        }
        return null
    }

    private fun handleBroadcastBlock(networkMessage: NetworkMessage) {
        if (networkMessage.sender == Ledger.myLedgerEntry?.userName) return
        Log.d(TAG, "Broadcast block received ${networkMessage.payload}")
        val blockString = networkMessage.payload
        val block = LedgerEntry.parseString(blockString)
        val publicKey = block.certificate.publicKey
        val isValidSignature = PKIUtils.verifySignature(blockString, networkMessage.signature, publicKey, null)
        if(isValidSignature) {
            Ledger.addLedgerEntry(block)
        }
    }

    // TODO: Should not send hash if there are CA-certified and you are not one of them
    private fun handleRequestedLedger(networkMessage: NetworkMessage) {
        if (!shouldHandleRequests || registrationHandlers[networkMessage.nonce] != null) return
        val registrationHandler = startRegistrationProcess(networkMessage.nonce, false) ?: return
        // if I started the registration, I will not send anything
        if (registrationHandler.isMyRegistration) return
        Log.d(TAG, "Received request for ledger with nonce: ${networkMessage.nonce}.")
        // must be a copy of the real list
        val fullLedger = Ledger.availableDevices.toList()
        val myBlock = Ledger.myLedgerEntry
        if (fullLedger.isNotEmpty() && myBlock != null) {
            GlobalScope.launch (Dispatchers.IO) {
                if (Ledger.shouldSendFullLedger()) {
                    // register your own ledger as a vote in your own registration process
                    registrationHandler.fullLedgerReceived(ReceivedLedger(fullLedger, Ledger.getHashOfLedger(fullLedger) , myBlock))
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
            if (usernameToReply == Ledger.myLedgerEntry?.userName && Ledger.getHashOfStoredLedger() == hash) {
                GlobalScope.launch(Dispatchers.IO) {
                    client.sendLedger(networkMessage.nonce)
                }
            }
        }
    }

    private fun handleFullLedger(networkMessage: NetworkMessage) {
        if (networkMessage.sender == Ledger.myLedgerEntry?.userName) return
        val ledger: ReceivedLedger = handleLedgerFragment(networkMessage) ?: return
        val registrationHandler = startRegistrationProcess(networkMessage.nonce, false) ?: return
        Log.d(TAG, "FULL_LEDGER ${networkMessage.nonce}")
        registrationHandler.fullLedgerReceived(ledger)
    }

    /**
     * @return if ledger is complete, the ledger is returned, if not, the fragment is stored and null is returned
     */
    private fun handleLedgerFragment(networkMessage: NetworkMessage): ReceivedLedger? {
        // if the ledger is separated into several packets, handle the fragment...
        if (networkMessage.lastSequenceNumber > 0) {
            Log.d(TAG, "Received fragment ${networkMessage.sequenceNumber} of ${networkMessage.lastSequenceNumber}: ${networkMessage.payload}")
            if (networkMessage.sequenceNumber == 0) {
                return handleFirstPacket(networkMessage)
            } else {
                val possibleCertificates = usernameToCertificates[networkMessage.sender]
                val correctCertificate = possibleCertificates?.find { PKIUtils.verifySignature(networkMessage.payload, networkMessage.signature, it.publicKey, networkMessage.nonce) }
                val ledgerSequenceId = if (correctCertificate != null) "${networkMessage.nonce}:${PKIUtils.certificateToString(correctCertificate)}" else null
                val ledgerFragments = ledgerFragmentsReceived[ledgerSequenceId]
                // if ledger fragments is null the first block in the sequence has not been received
                // and the signature cannot be validated, so the packet should be stored as an orphan
                // null should be returned
                if (ledgerFragments == null || correctCertificate == null) {
                    val existingOrphanOfSameUsername = orphanFragment[networkMessage.sender]
                    if (existingOrphanOfSameUsername != null) {
                        existingOrphanOfSameUsername.add(networkMessage)
                    } else {
                        orphanFragment[networkMessage.sender] = mutableListOf(networkMessage)
                    }
                    return null
                }
                // if we have not received this message before it should be stored
                if (ledgerFragments[networkMessage.sequenceNumber] == null) ledgerFragments[networkMessage.sequenceNumber] = networkMessage.payload
                Log.d(TAG, "Fragments received: $ledgerFragments")
                // if all fragments have not yet been received, we should return
                if (ledgerFragments.count { it == null } > 0) return null
                // if all fragments have been received we should return the ledger
                val senderBlock = certificateStringToSenderBlock[PKIUtils.certificateToString(correctCertificate)]
                val ledger = formatLedgerFragments(ledgerFragments as MutableList<String>)
                val hash = Ledger.getHashOfLedger(ledger)
                return ReceivedLedger(ledger, hash, senderBlock!!)
            }
            // ... if not, return the full ledger
        } else {
            Log.d(TAG, "Received full ledger: $networkMessage")
            val senderBlock = LedgerEntry.parseString(networkMessage.sender)
            // return null if signature is not valid
            if (!PKIUtils.verifySignature(networkMessage.payload, networkMessage.signature, senderBlock.certificate.publicKey, networkMessage.nonce)) return null
            val ledger = formatLedgerFragments(mutableListOf(networkMessage.payload))
            val hash = Ledger.getHashOfLedger(ledger)
            return ReceivedLedger(ledger, hash, senderBlock)
        }
    }

    private fun formatLedgerFragments(fragments: MutableList<String>): List<LedgerEntry> {
        val ledgerEntries: MutableList<LedgerEntry> = mutableListOf()
        // add every entry from every fragment
        fragments.forEach { fragment -> fragment.split(", ").forEach { entry -> ledgerEntries.add(LedgerEntry.parseString(entry)) } }
        return ledgerEntries
    }

    private fun handleFirstPacket(networkMessage: NetworkMessage): ReceivedLedger? {
        val blockOfSender = LedgerEntry.parseString(networkMessage.sender)
        if (!PKIUtils.verifySignature(networkMessage.payload, networkMessage.signature, blockOfSender.certificate.publicKey, networkMessage.nonce)) return null
        val certificatesToUsername = usernameToCertificates[blockOfSender.userName]
        if (certificatesToUsername == null) {
            usernameToCertificates[blockOfSender.userName] =
                mutableListOf(blockOfSender.certificate)
        } else if (certificatesToUsername.map { PKIUtils.certificateToString(it) }.contains(PKIUtils.certificateToString(blockOfSender.certificate))) {
            certificatesToUsername.add(blockOfSender.certificate)
        }
        val certificateString = PKIUtils.certificateToString(blockOfSender.certificate)
        certificateStringToSenderBlock[certificateString] = blockOfSender
        val ledgerSequenceId = "${networkMessage.nonce}:${certificateString}"
        val ledgerFragments = ledgerFragmentsReceived[ledgerSequenceId]
        if (ledgerFragments == null) {
            ledgerFragmentsReceived[ledgerSequenceId] = MutableList(networkMessage.lastSequenceNumber + 1) {index -> if (index == 0) networkMessage.payload else null }
        } else if (ledgerFragments[networkMessage.sequenceNumber] == null) {
            ledgerFragments[networkMessage.sequenceNumber] = networkMessage.payload
        }
        val orphanFragmentsFromSameUsername = orphanFragment[blockOfSender.userName]
        if (orphanFragmentsFromSameUsername != null) {
            val ledgerFragments = ledgerFragmentsReceived[ledgerSequenceId] ?: return null
            orphanFragmentsFromSameUsername.forEach {
                // if block was signed by sender of this block...
                if (PKIUtils.verifySignature(it.payload, it.signature, blockOfSender.certificate.publicKey, it.nonce)) {
                    // ... and the fragment is not already added, add it
                    if (ledgerFragments[it.sequenceNumber] == null) ledgerFragments[it.sequenceNumber] = networkMessage.payload
                }
            }
            if (ledgerFragments.count{it == null} == 0) {
                val ledger = formatLedgerFragments(ledgerFragments as MutableList<String>)
                val hash = Ledger.getHashOfLedger(ledger)
                return ReceivedLedger(ledger, hash, blockOfSender)
            }
        }
        return null
    }

    private fun handleHash(networkMessage: NetworkMessage) {
        val registrationHandler = startRegistrationProcess(networkMessage.nonce, false) ?: return
        val senderBlock = LedgerEntry.parseString(networkMessage.sender)
        if (senderBlock.userName == Ledger.myLedgerEntry?.userName) return
        Log.d(TAG, "Received hash from ${senderBlock.userName}: ${networkMessage.payload}")
        val publicKey = senderBlock.certificate.publicKey ?: throw Exception("Can not handle full ledger - Could not find public key for user")
        val isValidSignature = PKIUtils.verifySignature(networkMessage.payload, networkMessage.signature, publicKey, networkMessage.nonce)
        if (isValidSignature) {
            registrationHandler.hashOfLedgerReceived(senderBlock, networkMessage.payload)
        }
    }

    private fun handleIpChanged(networkMessage: NetworkMessage) {
        val senderBlock = LedgerEntry.parseString(networkMessage.sender)
        if (senderBlock.userName == Ledger.myLedgerEntry?.userName) return
        val publicKey = senderBlock.certificate.publicKey ?: throw Exception("Can not handle ip changed - Could not find public key for user")
        val blockToChange = Ledger.getLedgerEntry(senderBlock.userName) ?: return
        val newIp = networkMessage.payload.split(":").first()
        val senderHasCorrectCertificate = PKIUtils.certificateToString(blockToChange.certificate) == PKIUtils.certificateToString(senderBlock.certificate)
        val isValidSignature = PKIUtils.verifySignature(networkMessage.payload, networkMessage.signature, publicKey, null)
        val isValidTimestamp = isValidTimestampFromIpMessage(networkMessage.payload)
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
        super.onDestroy()

    }

    internal class NetworkCallbackImp(private val server: MulticastServer) : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            if (server.currentNetwork != network) {
                server.currentNetwork = network
                Ledger.clearLedger()
                val nonce = MISCUtils.generateNonce()
                if (isWifi) server.startRegistrationProcess(nonce, true)
                GlobalScope.launch(Dispatchers.IO) {
                    server.client.requestLedger(nonce)
                }
            }
        }
    }
}
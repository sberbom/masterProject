package com.example.masterproject.network

import android.content.Context
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.ledger.ConstructLedgerForTest
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.types.MulticastPacket
import com.example.masterproject.utils.Constants
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MulticastClient (private val server: MulticastServer?) {

    private val TAG = "MulticastClient"
    private val multicastGroup: String = Constants.multicastGroup
    private val multicastPort: Int = Constants.multicastPort
    private val context: Context? = App.getAppContext()

    private val ledgersSent: MutableMap<Int, List<String>> = mutableMapOf()

    private fun sendPacket(socket: DatagramSocket, msgs: List<String>, address: InetAddress) {
        msgs.forEach { msg ->
            val msgPacket = DatagramPacket(msg.toByteArray(), msg.toByteArray().size, address, multicastPort)
            socket.send(msgPacket)
        }
    }

    private fun sendMulticastData(msgs: List<String>): Void? {
        val address = InetAddress.getByName(multicastGroup)
        try {
            var serverSocket = DatagramSocket()
            Log.d(TAG, "Sent messages: $msgs")
            sendPacket(serverSocket, msgs, address)
            if (Constants.NUMBER_OF_RESENDS > 0) {
                repeat(Constants.NUMBER_OF_RESENDS) {
                    Thread.sleep((Constants.TOTAL_PACKET_WAIT / Constants.NUMBER_OF_RESENDS).toLong())
                    sendPacket(serverSocket, msgs, address)
                }
            }
            serverSocket.close()
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // TODO: The hash of the full ledger should be sent together with users own block
    suspend fun broadcastBlock(nonce: Int) {
        if(context == null) throw Exception("Could not broadcast block, context not defined")
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not broadcast block, private key not defined")
        val block = Ledger.myLedgerEntry ?: throw Exception("No block to broadcast.")
        val signature = PKIUtils.signMessage(block.toString(), privateKey, null)
        val message = MulticastPacket(block.userName, block.toString(), BroadcastMessageTypes.BROADCAST_BLOCK.toString(), signature, nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(listOf(message.toString()))
        }
    }

    suspend fun requestLedger(nonce: Int) {
        if (server == null) return
        val message = MulticastPacket("", "", BroadcastMessageTypes.REQUEST_LEDGER.toString(), "", nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(listOf(message.toString()))
        }
    }

    suspend fun sendLedger(nonce: Int) {
        if(context == null) throw Exception("Could not send ledger, context not defined")
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not send ledger, private not defined")
        val certificate = PKIUtils.getStoredCertificate() ?: throw Exception("Could not send ledger, username not defined")
        val username = PKIUtils.getUsernameFromCertificate(certificate)
        val currentLedger = Ledger.availableDevices.toList()
        /** Only for testing **/
        ConstructLedgerForTest.createLedger(5)
        val testLedger = ConstructLedgerForTest.ledger
        /***********************/
        // next line should use testLedger when testing and currentLedger if not
        val deconstructedLedger = testLedger.chunked(1)
        val multicastPackets = deconstructedLedger.mapIndexed { index, fragment ->
            val fragmentString = Ledger.toString(fragment).replace("[", "").replace("]", "")
            MulticastPacket(
                if (index == 0) Ledger.myLedgerEntry.toString() else username,
                fragmentString,
                BroadcastMessageTypes.FULL_LEDGER.toString(),
                PKIUtils.signMessage(fragmentString, privateKey, nonce),
                nonce,
                index,
                deconstructedLedger.size - 1
            ).toString()
        }
        ledgersSent[nonce] = multicastPackets
        return withContext(Dispatchers.IO) {
            sendMulticastData(multicastPackets)
        }
    }

    suspend fun sendHash(nonce: Int) {
        if(context == null) throw Exception("Could not broadcast block, context not defined")
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not send hash, private not defined")

        val myBlock = Ledger.myLedgerEntry
        val hash = Ledger.getHashOfStoredLedger()
        val signature = PKIUtils.signMessage(hash, privateKey, nonce)
        val message = MulticastPacket(myBlock.toString(), hash, BroadcastMessageTypes.LEDGER_HASH.toString(), signature, nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(listOf(message.toString()))
        }
    }

    suspend fun requestSpecificHash(hash: String, from: String) {
        val nonce = MISCUtils.generateNonce()
        val message = MulticastPacket("", "$from:$hash", BroadcastMessageTypes.REQUEST_SPECIFIC_LEDGER.toString(), "", nonce)
        return withContext(Dispatchers.IO) {
            sendMulticastData(listOf(message.toString()))
        }
    }

    suspend fun sendIpChanged(newIp: String) {
        val payload = MISCUtils.getIpMessageWithTimestamp(newIp)
        val privateKey = PKIUtils.getPrivateKeyFromKeyStore() ?: throw Exception("Could not send new ip address, private not defined")
        val signature = PKIUtils.signMessage(payload, privateKey, null)
        val message = MulticastPacket(Ledger.myLedgerEntry.toString(), payload, BroadcastMessageTypes.IP_CHANGED.toString(), signature)
        return withContext(Dispatchers.IO) {
            sendMulticastData(listOf(message.toString()))
        }
    }

}
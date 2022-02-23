package com.example.masterproject.network.unicast

import com.example.masterproject.crypto.Ratchet
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.Constants
import java.net.InetAddress
import java.net.Socket
import javax.crypto.SecretKey

class TCPClient(override val ledgerEntry: LedgerEntry): Client() {

    override val TAG = "TCPClient"
    override var encryptionKey: SecretKey? = AESUtils.getKeyForUser(ledgerEntry.userName)
    override var port = Constants.TCP_SERVERPORT
    override var ratchet: Ratchet? = Ratchet(ledgerEntry.userName)

    override fun encryptMessageSymmetric(message: String): String {
        val encryptionKey = ratchet!!.getEncryptionKey()
        return AESUtils.symmetricEncryption(message, encryptionKey)
    }

    override fun decryptMessagePayload(networkMessage: NetworkMessage, ledgerEntry: LedgerEntry): String {
        val encryptionKey = ratchet!!.getKey(networkMessage.ratchetKey)
        return  AESUtils.symmetricDecryption(networkMessage.payload, encryptionKey)
    }

    override fun createClientSocket(serverAddress: InetAddress): Socket {
        return Socket(serverAddress, Constants.TCP_SERVERPORT)
    }

    override fun getRatchetKeyRound(): Int {
        return ratchet!!.sendingRatchet
    }

}
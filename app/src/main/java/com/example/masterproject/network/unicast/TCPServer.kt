package com.example.masterproject.network.unicast

import com.example.masterproject.crypto.Ratchet
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import java.io.DataInputStream
import java.io.DataOutputStream

class TCPServer(override var inputStream: DataInputStream?, override var outputStream: DataOutputStream?): Server() {

    override val TAG = "TCPServer"

    override fun setEncryptionKeyAndUsername(networkMessage: NetworkMessage) {
        if(encryptionKey == null || username == null) {
            encryptionKey = AESUtils.getKeyForUser(networkMessage.sender)
            username = networkMessage.sender
            ratchet = Ratchet(networkMessage.sender)
        }
    }

    override fun decryptMessagePayload(networkMessage: NetworkMessage, ledgerEntry: LedgerEntry): String {
        val encryptionKey = ratchet!!.getKey(networkMessage.ratchetKey)
        return  AESUtils.symmetricDecryption(networkMessage.payload, encryptionKey)
    }

    override fun encryptMessageSymmetric(message: String ): String {
        val encryptionKey = ratchet!!.getEncryptionKey()
        return AESUtils.symmetricEncryption(message, encryptionKey)
    }

    override fun getRatchetKeyRound(): Int {
        return ratchet!!.sendingRatchet
    }

    override fun handleGoodbye() {
        inputStream?.close()
        outputStream?.close()
        super.handleGoodbye()
    }

}
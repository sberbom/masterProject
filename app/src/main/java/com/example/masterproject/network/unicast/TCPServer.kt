package com.example.masterproject.network.unicast

import com.example.masterproject.crypto.Ratchet
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.UnicastPacket
import com.example.masterproject.utils.AESUtils
import java.io.DataInputStream
import java.io.DataOutputStream

class TCPServer(override var inputStream: DataInputStream?, override var outputStream: DataOutputStream?): Server() {

    override val TAG = "TCPServer"

    override fun setEncryptionKeyAndUsername(unicastPacket: UnicastPacket) {
        if(encryptionKey == null || username == null) {
            encryptionKey = AESUtils.getKeyForUser(unicastPacket.sender)
            username = unicastPacket.sender
            ratchet = Ratchet(unicastPacket.sender)
        }
    }

    override fun decryptMessagePayload(unicastPacket: UnicastPacket, ledgerEntry: LedgerEntry): String {
        val encryptionKey = ratchet!!.getKey(unicastPacket.ratchetKey)
        return  AESUtils.symmetricDecryption(unicastPacket.payload, encryptionKey)
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
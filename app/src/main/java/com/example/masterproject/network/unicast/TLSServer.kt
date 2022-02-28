package com.example.masterproject.network.unicast

import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.UnicastPacket
import java.io.DataInputStream
import java.io.DataOutputStream

class TLSServer(override var outputStream: DataOutputStream?, override var inputStream: DataInputStream?): Server() {

    override val TAG = "TLSServer"

    override fun setEncryptionKeyAndUsername(unicastPacket: UnicastPacket) {
        if(username == null){
            username = unicastPacket.sender
        }
    }

    override fun decryptMessagePayload(unicastPacket: UnicastPacket, ledgerEntry: LedgerEntry): String {
        return unicastPacket.payload
    }

    override fun encryptMessageSymmetric(message: String): String {
        return message
    }

    override fun getRatchetKeyRound(): Int {
        return -1
    }

}
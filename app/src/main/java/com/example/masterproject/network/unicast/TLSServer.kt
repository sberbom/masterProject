package com.example.masterproject.network.unicast

import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.SecretKey

class TLSServer(override val outputStream: DataOutputStream, override val inputStream: DataInputStream): Server() {

    override val TAG = "TLSServer"

    override fun setEncryptionKeyAndUsername(networkMessage: NetworkMessage) {
        if(username == null){
            username = networkMessage.sender
        }
    }

    override fun decryptMessagePayload(networkMessage: NetworkMessage, encryptionKey: SecretKey?, ledgerEntry: LedgerEntry): String {
        return networkMessage.payload
    }

    override fun encryptMessageSymmetric(message: String, encryptionKey: SecretKey?): String {
        return message
    }

}
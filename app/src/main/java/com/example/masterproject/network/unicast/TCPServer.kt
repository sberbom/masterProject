package com.example.masterproject.network.unicast

import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.AESUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.Exception
import javax.crypto.SecretKey

class TCPServer(override val inputStream: DataInputStream , override val outputStream: DataOutputStream): Server() {

    override val TAG = "TCPServer"

    override fun setEncryptionKeyAndUsername(networkMessage: NetworkMessage) {
        if(encryptionKey == null || username == null) {
            encryptionKey = AESUtils.getCurrentKeyForUser(networkMessage.sender)
            username = networkMessage.sender
        }
    }

    override fun decryptMessagePayload(networkMessage: NetworkMessage, encryptionKey: SecretKey?, ledgerEntry: LedgerEntry): String {
        if(encryptionKey == null) throw Exception("No encryption key")
        return  AESUtils.symmetricDecryption(networkMessage.payload, encryptionKey, ledgerEntry)
    }

    override fun encryptMessageSymmetric(message: String, encryptionKey: SecretKey?): String {
        if(encryptionKey == null) throw Exception("No encryption key")
        return AESUtils.symmetricEncryption(message, encryptionKey)
    }

    override fun handleGoodbye() {
        AESUtils.useNextKeyForUser(username!!)
        inputStream.close()
        outputStream.close()
        super.handleGoodbye()
    }

}
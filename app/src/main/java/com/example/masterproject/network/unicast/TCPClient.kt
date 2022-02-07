package com.example.masterproject.network.unicast

import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.Constants
import java.lang.Exception
import java.net.InetAddress
import java.net.Socket
import javax.crypto.SecretKey

class TCPClient(override val ledgerEntry: LedgerEntry): Client() {

    override val TAG = "TCPClient"
    override var encryptionKey: SecretKey? = AESUtils.getCurrentKeyForUser(ledgerEntry.userName)
    override var port = Constants.TCP_SERVERPORT

    override fun encryptMessageSymmetric(message: String, encryptionKey: SecretKey?): String {
        if(encryptionKey == null) throw Exception("No encryption key")
       return AESUtils.symmetricEncryption(message, encryptionKey)
    }

    override fun decryptMessageSymmetric (message: String, encryptionKey: SecretKey?, ledgerEntry: LedgerEntry): String {
        if(encryptionKey == null) throw Exception("No encryption key")
        return AESUtils.symmetricDecryption(message, encryptionKey, ledgerEntry)
    }

    override fun createClientSocket(serverAddress: InetAddress): Socket {
        return Socket(serverAddress, Constants.TCP_SERVERPORT)
    }

    override fun closeSocket(){
        AESUtils.useNextKeyForUser(ledgerEntry.userName)
        super.closeSocket()
    }

}
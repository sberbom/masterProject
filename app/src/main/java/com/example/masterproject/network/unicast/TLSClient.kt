package com.example.masterproject.network.unicast

import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.utils.Constants
import com.example.masterproject.utils.PKIUtils
import java.net.InetAddress
import java.net.Socket
import javax.crypto.SecretKey
import javax.net.ssl.SSLSocket


class TLSClient(override val ledgerEntry: LedgerEntry): Client() {

    override val TAG = "TLSClient"
    override val encryptionKey: SecretKey? = null
    override val port = Constants.TLS_SERVERPORT

    override fun encryptMessageSymmetric(message: String ): String {
        return message
    }

    override fun decryptMessageSymmetric(message: String, ledgerEntry: LedgerEntry, ratchatKey: Int): String {
        return message
    }

    override fun createClientSocket(serverAddress: InetAddress): Socket {
       val clientSocket = PKIUtils.createSSLContext().socketFactory.createSocket(serverAddress, Constants.TLS_SERVERPORT) as SSLSocket
        clientSocket.enabledProtocols = arrayOf(Constants.TLS_VERSION)
        return clientSocket
    }

    override fun getRatchetKeyRound(): Int {
        return -1
    }

}
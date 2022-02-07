package com.example.masterproject.network.unicast

import com.example.masterproject.utils.Constants
import com.example.masterproject.utils.PKIUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import javax.net.ssl.SSLServerSocket


class TLSListener: ConnectionListener() {

    override val TAG = "TLSListener"

    override fun startServer(inputStream: DataInputStream, outputStream: DataOutputStream, username: String) {
        val tlsServer = TLSServer(outputStream, inputStream)
        Thread(tlsServer).start()
        ServerMap.serverMap[username] = tlsServer
    }

    override fun createSocket(): ServerSocket {
        val serverSocket = PKIUtils.createSSLContext().serverSocketFactory.createServerSocket(Constants.TLS_SERVERPORT) as SSLServerSocket
        serverSocket.enabledProtocols = arrayOf(Constants.TLS_VERSION)
        serverSocket.needClientAuth = true
        return serverSocket
    }

}
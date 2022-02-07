package com.example.masterproject.network.unicast

import com.example.masterproject.utils.Constants
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket

class TCPListener: ConnectionListener() {

    override val TAG = "TCPListener"

    override fun startServer(inputStream: DataInputStream, outputStream: DataOutputStream, username: String) {
        val tcpServer = TCPServer(inputStream, outputStream)
        Thread(tcpServer).start()
        ServerMap.serverMap[username] = tcpServer
    }

    override fun createSocket(): ServerSocket {
       return ServerSocket(Constants.TCP_SERVERPORT)
    }

}
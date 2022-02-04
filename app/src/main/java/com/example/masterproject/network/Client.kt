package com.example.masterproject.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

abstract class Client: Thread() {
   abstract val inputStream: DataInputStream
   abstract val outputStream: DataOutputStream
   abstract val clientSocket: Socket

    abstract fun sendMessage(message: String, messageType: String)
    open fun closeSocket() {
        clientSocket.close()
    }
}
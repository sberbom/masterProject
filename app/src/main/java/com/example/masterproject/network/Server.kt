package com.example.masterproject.network

abstract class Server: Thread() {
    abstract fun sendMessage(message: String, messageType: String)
}
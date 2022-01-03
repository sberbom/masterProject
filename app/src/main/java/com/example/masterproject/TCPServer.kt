package com.example.masterproject

import android.os.Handler
import android.widget.TextView
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class TCPServer(private val messageView: TextView): Runnable {

    var updateConversationHandler: Handler = Handler()

    override fun run() {
        try {
            val serverSocket = ServerSocket(SERVERPORT)
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val commThread = CommunicationThread(serverSocket.accept())
                    Thread(commThread).start()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /*
    fun closeSocket() {
        try {
            serverSocket!!.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    */

    internal inner class CommunicationThread(clientSocket: Socket) : Runnable {

        private var input: BufferedReader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

        override fun run() {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val readEncrypted = input.readLine()
                    val read = Utils.decryptMessage(readEncrypted, Utils.keyPair!!.private)
                    updateConversationHandler.post(UpdateUIThread(read))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        internal inner class UpdateUIThread(private val msg: String) : Runnable {
            override fun run() {
                messageView.text = "TCP message received: $msg\n"
            }
        }
    }

    companion object {
        const val SERVERPORT = 7000
    }

}
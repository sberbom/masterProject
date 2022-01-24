package com.example.masterproject

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class TCPServer(private val messageView: TextView): Runnable {

    var updateConversationHandler: Handler = Handler(Looper.getMainLooper())

    override fun run() {
        try {
            val serverSocket = ServerSocket(Constants.SERVERPORT)
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val commThread = CommunicationThread(serverSocket.accept())
                    Thread(commThread).start()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            serverSocket.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    internal inner class CommunicationThread(serverSocket: Socket) : Runnable {

        private var input = DataInputStream(serverSocket.getInputStream())

        override fun run() {
            try {
                val read = input.readUTF()
                val arrOfRead = read.split(":://")
                val userName = arrOfRead[0]
                val ledgerEntry = Ledger.getLedgerEntry(userName)
                val readEncrypted = arrOfRead[1]
                if(readEncrypted != null && ledgerEntry != null) {
                    val sharedKey = EncryptionUtils.calculateAESKeys(Utils.privateKey!!, ledgerEntry.certificate)
                    val read = EncryptionUtils.symmetricDecryption(readEncrypted, sharedKey)
                    if(read != null){
                        updateConversationHandler.post(UpdateUIThread(read))
                    }
                }
                else {
                    Thread.currentThread().interrupt()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        internal inner class UpdateUIThread(private val msg: String) : Runnable {
            override fun run() {
                messageView.text = "TCP message received: $msg\n"
            }
        }
    }

}
package com.example.masterproject

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.*
import java.lang.IllegalArgumentException
import java.net.ServerSocket
import java.net.Socket

class TCPServer(val context: Context): Runnable {

    var updateConversationHandler: Handler = Handler(Looper.getMainLooper())
    private val TAG = "TCPServer"

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
                when(val readEncrypted = arrOfRead[1]) {
                    Constants.CLIENT_HELLO -> changeToChatActivity(ledgerEntry!!)
                    Constants.KEY_DELEVERY -> storeNextKey(arrOfRead[2], ledgerEntry!!)
                    else -> updateConversationHandler.post(UpdateUIThread(userName, readEncrypted, ledgerEntry!!))
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        private fun storeNextKey(key: String, ledgerEntry: LedgerEntry) {
            val sharedKey = AESUtils.getEncryptionKey(ledgerEntry.userName)
            val decrypted = AESUtils.symmetricDecryption(key, sharedKey, ledgerEntry)
            try {
                val nextKey = AESUtils.stringToKey(decrypted)
                AESUtils.setNextKeyForUser(ledgerEntry.userName, nextKey)
            }
            catch (e: IllegalArgumentException) {
                Log.d(TAG, decrypted)
                e.printStackTrace()
            }
        }

        private fun changeToChatActivity(ledgerEntry: LedgerEntry) {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("userName", ledgerEntry.userName)
            intent.putExtra("staringNewConnection", false)
            context.startActivity(intent)
        }

        internal inner class UpdateUIThread(private val userName: String, private val msg: String, private val ledgerEntry: LedgerEntry) : Runnable {
            override fun run() {
                val sharedKey = AESUtils.getEncryptionKey(userName)
                val decrypted = AESUtils.symmetricDecryption(msg, sharedKey, ledgerEntry)
                Handler(Looper.getMainLooper()).post {
                    ChatActivity.addChat(userName, decrypted)
                }
            }
        }
    }

}
package com.example.masterproject.network

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.App
import com.example.masterproject.utils.Constants
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.lang.IllegalArgumentException
import java.net.ServerSocket
import java.net.Socket

class TCPServer: Service() {

    var updateConversationHandler: Handler = Handler(Looper.getMainLooper())
    private val TAG = "TCPServer"
    private var serverSocket: ServerSocket? = null
    private val context = App.getAppContext()

    private fun listenForConnections() {
        Log.d(TAG, "Listening for connections.")
        val socket = serverSocket
        while (!Thread.currentThread().isInterrupted && socket != null && !socket.isClosed) {
            try {
                listenForMessages(serverSocket?.accept())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun listenForMessages(socket: Socket?) {
        if (socket == null) return
        val input = DataInputStream(socket.getInputStream())
        try {
            val read = input.readUTF()
            Log.d(TAG, "Received data: $read")
            val arrOfRead = read.split(":://")
            val userName = arrOfRead[0]
            val ledgerEntry = Ledger.getLedgerEntry(userName)
            when(val readEncrypted = arrOfRead[1]) {
                UnicastMessageTypes.CLIENT_HELLO.toString() -> changeToChatActivity(ledgerEntry!!)
                UnicastMessageTypes.KEY_DELEVERY.toString() -> storeNextKey(arrOfRead[2], ledgerEntry!!)
                else -> updateConversationHandler.post(UpdateUIThread(userName, readEncrypted, ledgerEntry!!))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun storeNextKey(key: String, ledgerEntry: LedgerEntry) {
        if (context == null) return
        val sharedKey = AESUtils.getEncryptionKey(ledgerEntry.userName, context)
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
        if (context == null) return
        val intent = Intent(context, ChatActivity::class.java)
        intent.putExtra("userName", ledgerEntry.userName)
        intent.putExtra("staringNewConnection", false)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

    }

    internal inner class UpdateUIThread(private val userName: String, private val msg: String, private val ledgerEntry: LedgerEntry) : Runnable {
        override fun run() {
            if (context == null) return
            val sharedKey = AESUtils.getEncryptionKey(userName, context)
            val decrypted = AESUtils.symmetricDecryption(msg, sharedKey, ledgerEntry)
            Handler(Looper.getMainLooper()).post {
                ChatActivity.addChat(userName, decrypted)
            }
        }
    }

    override fun onCreate() {
        try {
            Log.d(TAG, "TCPServer started")
            serverSocket = ServerSocket(Constants.SERVERPORT)
            GlobalScope.launch {
                listenForConnections()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serverSocket?.close()
        super.onDestroy()
    }
}
package com.example.masterproject.network.unicast

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.types.UnicastPacket
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket

abstract class ConnectionListener: Service() {

    abstract val TAG: String
    var serverSocket: ServerSocket? = null
    var context: Context? = App.getAppContext()

    abstract fun startServer(inputStream: DataInputStream, outputStream: DataOutputStream, username: String)
    abstract fun createSocket(): ServerSocket

    override fun onCreate() {
        try {
            Log.d(TAG, "$TAG Started")
            serverSocket = createSocket()
            GlobalScope.launch {
                listenForConnections()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun listenForConnections() {
        Log.d(TAG, "Listening for connections.")
        val socket = serverSocket
        while (!Thread.currentThread().isInterrupted && !socket!!.isClosed) {
            try {
                val clientSocket = serverSocket!!.accept()
                val inputStream = DataInputStream(clientSocket.getInputStream())
                val outputStream = DataOutputStream(clientSocket.getOutputStream())
                val message = inputStream.readUTF()
                val username = UnicastPacket.decodeUnicastPacket(message).sender
                startServer(inputStream, outputStream, username)
                changeToChatActivity(username)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun changeToChatActivity(username: String) {
        if (context == null) return
        val intent = Intent(context, ChatActivity::class.java)
        intent.putExtra("userName", username)
        intent.putExtra("isClient", false)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context!!.startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
       return null
    }

    override fun onDestroy() {
        serverSocket!!.close()
        super.onDestroy()
    }

}
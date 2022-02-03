package com.example.masterproject.network

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.App
import com.example.masterproject.utils.Constants
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.types.NetworkMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.net.ServerSocket

class TCPListener: Service() {

    private val TAG = "TCPListener"
    private lateinit var serverSocket: ServerSocket
    private val context = App.getAppContext()

    private fun listenForConnections() {
        Log.d(TAG, "Listening for connections.")
        val socket = serverSocket
        while (!Thread.currentThread().isInterrupted && !socket.isClosed) {
            try {
                val clientSocket = serverSocket.accept()
                val inputStream = DataInputStream(clientSocket.getInputStream())
                val outputStream = DataOutputStream(clientSocket.getOutputStream())
                val message = inputStream.readUTF()
                val username = NetworkMessage.decodeNetworkMessage(message).sender
                if(AESUtils.getCurrentKeyForUser(username) == null) {
                    val tlsServer = TLSServer(outputStream, inputStream)
                    Thread(tlsServer).start()
                    ServerMap.serverMap[username] = tlsServer
                }else {
                    val tcpServer = TCPServer(inputStream, outputStream)
                    Thread(tcpServer).start()
                    ServerMap.serverMap[username] = tcpServer
                }
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
        context.startActivity(intent)
    }

    override fun onCreate() {
        try {
            Log.d(TAG, "TCPListener started")
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
        serverSocket.close()
        super.onDestroy()
    }
}
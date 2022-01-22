package com.example.masterproject

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.Exception
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.crypto.SecretKey


class TCPClient() {

    fun sendMessage(ledgerEntry: LedgerEntry, message: String){
        try {
            val serverAddress = InetAddress.getByName(ledgerEntry.ipAddress)
            val socket = Socket(serverAddress, Constants.SERVERPORT)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeUTF("${Utils.getUsernameFromCertificate(Utils.getCertificate()!!)}:://$message")
            out.flush()
            socket.close()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun sendEncryptedMessage(ledgerEntry: LedgerEntry, message: String, encryptionKey: SecretKey) {
        val encryptedMessage = AESUtils.symmetricEncryption(message, encryptionKey)
        Handler(Looper.getMainLooper()).post {
            ChatActivity.addChat("You:", message)
        }
        return withContext(Dispatchers.IO) {
            sendMessage(ledgerEntry, encryptedMessage)
        }
    }
}
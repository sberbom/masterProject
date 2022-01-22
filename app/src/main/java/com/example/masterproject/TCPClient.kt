package com.example.masterproject

import android.util.Log
import java.io.*
import java.lang.Exception
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ThreadLocalRandom


class TCPClient(private val userName:String, private val message: String): Runnable {

    override fun run() {
        try {
            val ledgerEntry = Ledger.getLedgerEntry(userName)
            if(ledgerEntry !== null) {
                sendMsg(ledgerEntry)
            }else {
                Log.ERROR
            }
        } catch (e1: UnknownHostException) {
            e1.printStackTrace()
        } catch (e1: IOException) {
            e1.printStackTrace()
        }
    }


    private fun sendMsg(ledgerEntry: LedgerEntry): Void? {
        try {
            val serverAddress = InetAddress.getByName(ledgerEntry.ipAddress)
            val socket = Socket(serverAddress, Constants.SERVERPORT)
            val str = if (message == "" || message == "Skriv en melding") ThreadLocalRandom.current().nextInt(0, 100).toString() else message
            val sharedKey = EncryptionUtils.calculateAESKeys(Utils.privateKey!!, ledgerEntry.certificate)
            val encryptedMessage = EncryptionUtils.symmetricEncryption(str, sharedKey)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeUTF("${Utils.getUsernameFromCertificate(Utils.getCertificate()!!)}:://$encryptedMessage")
            out.flush()
            socket.close()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
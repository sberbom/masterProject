package com.example.masterproject

import android.util.Log
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
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
            val socket = Socket(serverAddress, SERVERPORT)
            val str = if (message == "" || message == "Skriv en melding") ThreadLocalRandom.current().nextInt(0, 100).toString() else message
            val encryptedMessage = Utils.encryptMessage(str, ledgerEntry.certificate.publicKey)
            val out = PrintWriter(
                BufferedWriter(
                    OutputStreamWriter(
                        socket.getOutputStream()
                    )
                ), true
            )
            out.println(encryptedMessage)
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

    companion object {
        private const val SERVERPORT = 7000
    }
}
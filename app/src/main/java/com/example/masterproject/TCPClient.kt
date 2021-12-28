package com.example.masterproject

import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.Exception
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ThreadLocalRandom

class TCPClient(private val ip:String): Runnable {

    override fun run() {
        try {
            sendMsg(ip)
        } catch (e1: UnknownHostException) {
            e1.printStackTrace()
        } catch (e1: IOException) {
            e1.printStackTrace()
        }
    }


    fun sendMsg(serverIp: String): Void? {
        try {
            val serverAddr = InetAddress.getByName(serverIp)
            val socket = Socket(serverAddr, SERVERPORT)
            val str = ThreadLocalRandom.current().nextInt(0, 100).toString()
            val out = PrintWriter(
                BufferedWriter(
                    OutputStreamWriter(
                        socket!!.getOutputStream()
                    )
                ), true
            )
            out.println(str)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null;
    }

    companion object {
        private const val SERVERPORT = 7000
    }
}
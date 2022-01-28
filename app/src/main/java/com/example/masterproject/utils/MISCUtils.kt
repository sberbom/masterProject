package com.example.masterproject.utils

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.io.File
import java.lang.Exception
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.*

class MISCUtils {

    companion object {
        private const val TAG = "MISCUtils"

        fun getMyIpAddress(): String? {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val networkInterface: NetworkInterface = en.nextElement()
                val enumIpAddress: Enumeration<InetAddress> = networkInterface.inetAddresses
                while (enumIpAddress.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddress.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
            return null
        }

        fun isEmail(email: String): Boolean {
            return !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email)
                .matches();
        }

        fun hashString(input: String): String {
            val bytes = input.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.fold("", { str, it -> str + "%02x".format(it) })
        }

        fun deleteCache(context: Context) {
            try {
                val dir: File = context.cacheDir
                deleteDir(dir)
            }catch (e: Exception) {
                Log.w(TAG, "Could not delete cache")
                e.printStackTrace()
            }
        }

        private fun deleteDir(dir: File?): Boolean {
            return if (dir != null && dir.isDirectory) {
                val children = dir.list()
                if (children != null) {
                    for (i in children.indices) {
                        val success: Boolean = deleteDir(File(dir, children[i]))
                        if (!success) {
                            return false
                        }
                    }
                }
                dir.delete()
            } else if (dir != null && dir.isFile) {
                dir.delete()
            } else {
                false
            }
        }

        fun getCurrentUserString(context: Context): String {
            if (Firebase.auth.currentUser != null) {
                return Firebase.auth.currentUser!!.email!!
            }
            else if(PKIUtils.fetchStoredCertificate(context) != null){
                return PKIUtils.getUsernameFromCertificate(PKIUtils.fetchStoredCertificate(context)!!)
            }
            return "Not logged in"
        }

        fun isLoggedIn(context: Context): Boolean {
            return (Firebase.auth.currentUser != null || PKIUtils.fetchStoredCertificate(context) != null)
        }

        fun addYear(date: Date, i: Int): Date {
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.YEAR, i)
            return cal.time
        }
    }
}
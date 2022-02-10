package com.example.masterproject.ledger

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.utils.PKIUtils
import org.json.JSONObject
import org.json.JSONTokener
import java.security.cert.X509Certificate

data class LedgerEntry(
        val certificate: X509Certificate,
        val userName: String,
        private var ipAddress: String = ""
    ) {

    override fun toString(): String {
        return "{\"username\":\"$userName\"," +
                "\"ipAddress\":\"$ipAddress\"," +
                "\"certificate\":\"${PKIUtils.certificateToString(certificate)}\"}"
    }

    fun setIpAddress(ip: String) {
        ipAddress = ip
        Log.d(TAG, "Set ip of $userName to $ip")
        Handler(Looper.getMainLooper()).post {
            MainActivity.updateAvailableDevices()
        }
    }

    fun getIpAddress(): String {
        return ipAddress
    }

    companion object {
        private const val TAG = "LedgerEntry"

        fun parseString(ledgerEntry: String): LedgerEntry {
            val jsonObject = JSONTokener(ledgerEntry).nextValue() as JSONObject
            return LedgerEntry(
                PKIUtils.stringToCertificate(jsonObject.getString("certificate")),
                jsonObject.getString("username"),
                jsonObject.getString("ipAddress")
            )
        }

        fun ledgerEntryIsValid(ledgerEntry: LedgerEntry): Boolean {
            val userNameFromCertificate = PKIUtils.getUsernameFromCertificate(ledgerEntry.certificate)
            return userNameFromCertificate == ledgerEntry.userName
        }

        fun isEqual(first: LedgerEntry, second: LedgerEntry): Boolean {
            return first.userName == second.userName &&
                    PKIUtils.certificateToString(first.certificate) == PKIUtils.certificateToString(second.certificate)
        }
    }
}

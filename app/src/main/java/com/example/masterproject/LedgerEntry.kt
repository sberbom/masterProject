package com.example.masterproject

import android.util.Log
import org.json.JSONObject
import org.json.JSONTokener
import java.security.cert.X509Certificate

data class LedgerEntry(val certificate: X509Certificate, val userName: String, val ipAddress: String = "") {

    override fun toString(): String {
        return "{\"username\":\"$userName\",\"ipAddress\":\"$ipAddress\",\"certificate\":\"${Utils.certificateToString(certificate)}\"}"
    }

    companion object {
        private val TAG = "LedgerEntry"
        fun parseString(ledgerEntry: String): LedgerEntry {
            val jsonObject = JSONTokener(ledgerEntry).nextValue() as JSONObject
            Log.d(TAG, jsonObject.toString())
            return LedgerEntry(Utils.stringToCertificate(jsonObject.getString("certificate")), jsonObject.getString("username"), jsonObject.getString("ipAddress"))
        }
    }
}

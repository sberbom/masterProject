package com.example.masterproject

import android.util.Log
import org.json.JSONObject
import org.json.JSONTokener
import java.security.cert.X509Certificate

data class LedgerEntry(
        val certificate: X509Certificate,
        val userName: String,
        val ipAddress: String = "",
        val previousBlockHash: String,
        val height: Int
    ) {

    override fun toString(): String {
        return "{\"username\":\"$userName\"," +
                "\"ipAddress\":\"$ipAddress\"," +
                "\"previousBlockHash\":\"$previousBlockHash\"," +
                "\"height\":\"$height\"," +
                "\"certificate\":\"${Utils.certificateToString(certificate)}\"}"
    }

    companion object {
        private const val TAG = "LedgerEntry"

        fun parseString(ledgerEntry: String): LedgerEntry {
            val jsonObject = JSONTokener(ledgerEntry).nextValue() as JSONObject
            Log.d(TAG, jsonObject.toString())
            return LedgerEntry(
                Utils.stringToCertificate(jsonObject.getString("certificate")),
                jsonObject.getString("username"),
                jsonObject.getString("ipAddress"),
                jsonObject.getString("previousBlockHash"),
                jsonObject.getString("height").toInt()
            )
        }

        fun ledgerEntryIsValid(ledgerEntry: LedgerEntry): Boolean {
            val userNameFromCertificate = Utils.getUsernameFromCertificate(ledgerEntry.certificate)
            return userNameFromCertificate == ledgerEntry.userName
        }
    }
}

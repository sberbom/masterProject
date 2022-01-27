package com.example.masterproject.ledger

import com.example.masterproject.utils.PKIUtils
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
                "\"certificate\":\"${PKIUtils.certificateToString(certificate)}\"}"
    }

    companion object {
        private const val TAG = "LedgerEntry"

        fun parseString(ledgerEntry: String): LedgerEntry {
            val jsonObject = JSONTokener(ledgerEntry).nextValue() as JSONObject
            return LedgerEntry(
                PKIUtils.stringToCertificate(jsonObject.getString("certificate")),
                jsonObject.getString("username"),
                jsonObject.getString("ipAddress"),
                jsonObject.getString("previousBlockHash"),
                jsonObject.getString("height").toInt()
            )
        }

        fun ledgerEntryIsValid(ledgerEntry: LedgerEntry): Boolean {
            val userNameFromCertificate = PKIUtils.getUsernameFromCertificate(ledgerEntry.certificate)
            return userNameFromCertificate == ledgerEntry.userName
        }

        fun isEqual(first: LedgerEntry, second: LedgerEntry): Boolean {
            return first.userName == second.userName &&
                    first.certificate.toString() == second.certificate.toString() &&
                    first.height == second.height
        }
    }
}

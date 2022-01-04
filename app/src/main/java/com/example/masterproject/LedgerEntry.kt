package com.example.masterproject

import java.security.cert.X509Certificate

data class LedgerEntry(val certificate: X509Certificate, val userName: String, val ipAddress: String = "") {
}

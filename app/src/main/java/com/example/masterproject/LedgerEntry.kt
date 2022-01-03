package com.example.masterproject

import java.security.PublicKey

data class LedgerEntry(val publicKey: PublicKey, val userName: String, val ipAddress: String = "") {
}

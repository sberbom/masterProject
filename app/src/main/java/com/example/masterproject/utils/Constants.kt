package com.example.masterproject.utils

class Constants {
    companion object {
        const val multicastGroup: String = "224.0.0.10"
        const val multicastPort: Int = 8888
        const val TCP_SERVERPORT = 7000
        const val TLS_SERVERPORT = 7001
        const val KEY_FILE = "keyListFile"
        val TLS_VERSION = arrayOf("TLSv1.3")
        val TLS_CIPHER_SUITES = arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    }
}
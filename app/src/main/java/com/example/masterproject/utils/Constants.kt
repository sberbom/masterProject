package com.example.masterproject.utils

import android.annotation.SuppressLint
import android.content.Context

class Constants {
    companion object {
        const val multicastGroup: String = "224.0.0.10"
        const val multicastPort: Int = 8888
        const val TCP_SERVERPORT = 7000
        const val TLS_SERVERPORT = 7001
        const val KEY_FILE = "keyListFile"
        const val TLS_VERSION = "TLSv1.3"
        const val KEYSTORE_PATH = "/keystore.pfx"
        const val TRUSTSTORE_PATH = "/truststore.pfx"
        const val KEYSTORE_PASSWORD = "testing"
    }
}
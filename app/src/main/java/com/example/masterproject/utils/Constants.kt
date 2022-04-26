package com.example.masterproject.utils

class Constants {
    companion object {
        const val multicastGroup: String = "224.0.0.10"
        const val multicastPort: Int = 8888
        const val TCP_SERVERPORT = 7000
        const val TLS_SERVERPORT = 7001
        const val TLS_VERSION = "TLSv1.3"
        const val SSL_TYPE = "TLS"
        const val KEYSTORE_TYPE = "PKCS12"
        const val KEYSTORE_PATH = "/keystore.pfx"
        const val TRUSTSTORE_PATH = "/truststore.pfx"
        const val SYMMETRIC_KEYSTORE_PATH = "/symmetrickeystore.pfx"
        const val KEYSTORE_PASSWORD = "testing"

        const val MESSAGE_DIGEST_HASH = "SHA-256"
        const val KEY_TYPE = "ECDH"
        const val SYMMETRIC_ENCRYPTION_ALGORITHM = "AES"
        const val SYMMETRIC_ENCRYPTION_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE = 128

        const val ASYMMETRIC_KEY_GENERATION_ALGORITHM = "EC"
        const val ASYMMETRIC_SIGNATURE_ALGORITHM = "SHA256withECDSA"
        const val CERTIFICATE_TYPE = "X.509"
        const val KEY_MANAGER_INSTANCE = "X509"
        const val CA_CN = "CN=TTM4905 CA"

        const val TOTAL_PACKET_WAIT = 300
        const val NUMBER_OF_RESENDS = 3

        const val INTER_PACKET_TIME = 5
        const val LEDGER_ACCEPTANCE_TIME: Long = 15000
        const val ALONE_IN_NETWORK_TIME: Long = 4000
        const val BACKOFF_TIME: Long = 500
        const val CHECK_NETWORK_AVAILABILITY_TIME: Long = 500
    }
}
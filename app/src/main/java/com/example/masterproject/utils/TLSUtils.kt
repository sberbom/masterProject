package com.example.masterproject.utils

import android.content.Context
import android.util.Log
import com.example.masterproject.App
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.Exception
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class TLSUtils {

    companion object {

        private val TAG = "TLSUtils"
        var keyStore: KeyStore? = null
        var trustStore: KeyStore = loadTrustStore()
        private var context: Context? = App.getAppContext()

        fun loadKeyStore(): KeyStore {
            val localKeyStore = KeyStore.getInstance("PKCS12")
            if(context == null) throw Exception("context is null")
            return try{
                val fileInputStream = FileInputStream("${context!!.filesDir}/${Constants.KEYSTORE_PATH}")
                localKeyStore.load(fileInputStream, Constants.KEYSTORE_PASSWORD.toCharArray())
                Log.d(TAG, "KEYSTORE LOADED")
                localKeyStore as KeyStore
            } catch (e: FileNotFoundException) {
                localKeyStore.load(null, Constants.KEYSTORE_PASSWORD.toCharArray())
                Log.d(TAG, "NEW KEYSTORE CREATED")
                localKeyStore as KeyStore
            } catch (e: EOFException) {
                localKeyStore.load(null, Constants.KEYSTORE_PASSWORD.toCharArray())
                Log.d(TAG, "NEW KEYSTORE CREATED")
                localKeyStore as KeyStore
            }
        }

        private fun loadTrustStore(): KeyStore {
            val localTrustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            return try{
                val fileInputStream = FileInputStream(Constants.TRUSTSTORE_PATH)
                localTrustStore.load(fileInputStream, Constants.TRUSTSTORE_PATH.toCharArray())
                Log.d(TAG, "TRUSTSTORE LOADED")
                localTrustStore as KeyStore
            } catch (e: FileNotFoundException) {
                localTrustStore.load(null, Constants.TRUSTSTORE_PATH.toCharArray())
                Log.d(TAG, "NEW TRUSTSTORE CREATED")
                localTrustStore as KeyStore
            }
        }

        fun createSSLContext(): SSLContext {
            val keyMangerFactory = KeyManagerFactory.getInstance("X509")
            keyMangerFactory.init(keyStore, Constants.KEYSTORE_PASSWORD.toCharArray())
            val keyManagers = keyMangerFactory.keyManagers

            val trustManagerFactory = TrustManagerFactory.getInstance("x509")
            trustManagerFactory.init(trustStore)
            val trustManagers = trustManagerFactory.trustManagers


            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagers, trustManagers, null)

            return sslContext
        }

        fun addKeyToKeyStore(key: PrivateKey, certificate: X509Certificate) {
            Log.d(TAG, "Added key to keystore: $key")
            keyStore!!.setKeyEntry("root", key, Constants.KEYSTORE_PASSWORD.toCharArray(), arrayOf(certificate))
        }

        fun addCertificateToTrustStore(alias: String, certificate: X509Certificate) {
            Log.d(TAG, "Added certificate to trustStore: $alias")
            trustStore.setCertificateEntry(alias, certificate)
        }

        fun writeKeyStoreToFile() {
            val outputStream = FileOutputStream("${context!!.filesDir}/${Constants.KEYSTORE_PATH}")
            keyStore!!.store(outputStream, Constants.KEYSTORE_PASSWORD.toCharArray())
            outputStream.close()
            Log.d(TAG, "KEYSTORE WRITTEN TO FILE")
        }
    }

}
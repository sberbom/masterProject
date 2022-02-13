package com.example.masterproject.utils

import android.content.Context
import android.util.Log
import com.example.masterproject.App
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


class PKIUtils {

    companion object {
        var privateKey: PrivateKey? = null
        private const val TAG: String = "PKIUtils"
        var CAPublicKey: PublicKey =
            stringToEncryptionKey("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnbbrYnAGkcNYD72o/H7jP2z91bQyA1B8GwUzsV0NG34RhpJ6xJMLxQT0kXwnSunXz6wVndN6O/6ZDWymVCk3nw==")
        var keyStore: KeyStore? = null
        var trustStore: KeyStore = loadTrustStore()
        private var context: Context? = App.getAppContext()

        fun generateECKeyPair(): KeyPair {
            val ECDSAGenerator = KeyPairGenerator.getInstance("EC");
            val keyPair = ECDSAGenerator.genKeyPair()
            privateKey = keyPair.private
            return keyPair
        }

        private fun isValidSelfSignedCertificate(certificate: X509Certificate): Boolean {
            return try {
                certificate.verify(certificate.publicKey)
                true
            } catch (e: Error) {
                Log.w(TAG, "INVALID SELF SIGN SIGNATURE ON CERTIFICATE")
                e.printStackTrace()
                false
            }
        }

        private fun isValidCACertificate(certificate: X509Certificate): Boolean {
            return try {
                certificate.verify(CAPublicKey)
                true
            } catch (e: Exception) {
                Log.w(TAG, "INVALID CA SIGNATURE ON CERTIFICATE")
                e.printStackTrace()
                false
            }
        }

        //https://www.bouncycastle.org/docs/pkixdocs1.5on/org/bouncycastle/cert/X509v1CertificateBuilder.html
        fun generateSelfSignedX509Certificate(username: String, keyPair: KeyPair): X509Certificate {
            val validityBeginDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            val validityEndDate = MISCUtils.addYear(Date(), 2)
            val certificateIssuer = X500Name("CN=$username")
            val certificateSerialNumber = BigInteger.valueOf(System.currentTimeMillis())
            val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded);
            val certificateBuilder = X509v1CertificateBuilder(
                certificateIssuer,
                certificateSerialNumber,
                validityBeginDate,
                validityEndDate,
                certificateIssuer,
                publicKeyInfo
            )
            val privateKeyInfo = PrivateKeyInfo.getInstance(keyPair.private.encoded)
            // This function takes a lot of time
            val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(
                JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
            )
            val holder: X509CertificateHolder = certificateBuilder.build(contentSigner)
            return JcaX509CertificateConverter().getCertificate(holder)
        }

        fun isSelfSignedCertificate(certificate: X509Certificate): Boolean {
            return certificate.issuerDN == certificate.subjectDN && isValidSelfSignedCertificate(certificate)
        }

        fun isCASignedCertificate(certificate: X509Certificate): Boolean {
            return certificate.issuerDN.toString() == "CN=TTM4905 CA" && isValidCACertificate(
                certificate
            )
        }

        fun getUsernameFromCertificate(certificate: X509Certificate): String {
            return certificate.subjectDN.toString().replace("CN=", "")
        }

        fun certificateToString(certificate: X509Certificate): String {
            return Base64.getEncoder().encodeToString(certificate.encoded)
        }

        fun stringToCertificate(string: String): X509Certificate {
            val encodedCertificate: ByteArray = Base64.getDecoder().decode(string)
            val inputStream = ByteArrayInputStream(encodedCertificate)

            val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
            return certificateFactory.generateCertificate(inputStream) as X509Certificate
        }

        private fun stringToEncryptionKey(key: String): PublicKey {
            val encKey = Base64.getDecoder().decode(key)
            val publicKeySpec = X509EncodedKeySpec(encKey)
            val keyFactory = KeyFactory.getInstance("EC")
            return keyFactory.generatePublic(publicKeySpec)
        }

        fun pemToCertificate(pem: String): X509Certificate {
            val beginCertificate = "-----BEGIN CERTIFICATE-----";
            val endCertificate = "-----END CERTIFICATE-----";
            val certificate =
                pem.replace(beginCertificate, "").replace(endCertificate, "").replace("\n", "")
            val decoded: ByteArray = Base64.getDecoder().decode(certificate)
            return (CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate)
        }

        fun encryptionKeyToPem(key: PublicKey): String {
            val beginCertificate = "-----BEGIN CERTIFICATE-----";
            val endCertificate = "-----END CERTIFICATE-----";
            val encodedKey = Base64.getEncoder().encodeToString(key.encoded)
            return "$beginCertificate\n$encodedKey\n$endCertificate"
        }

        fun getPrivateKeyFromKeyStore(): PrivateKey? {
            return (keyStore?.getEntry("root", KeyStore.PasswordProtection(Constants.KEYSTORE_PASSWORD.toCharArray())) as KeyStore.PrivateKeyEntry).privateKey
        }

        fun signMessage(message: String, privateKey: PrivateKey, nonce: Int?): String {
            val signatureBuilder = Signature.getInstance("SHA1withECDSA")
            signatureBuilder.initSign(privateKey)
            signatureBuilder.update((if (nonce == null) message else "$message:$nonce").toByteArray())
            return Base64.getEncoder().encodeToString(signatureBuilder.sign())
        }

        fun verifySignature(message: String, signature: String, publicKey: PublicKey, nonce: Int?): Boolean {
            val signatureBuilder = Signature.getInstance("SHA1withECDSA")
            signatureBuilder.initVerify(publicKey)
            signatureBuilder.update((if (nonce == null) message else "$message:$nonce").toByteArray())
            return signatureBuilder.verify(Base64.getDecoder().decode(signature))

        }

        fun loadKeyStore(): KeyStore {
            val localKeyStore = KeyStore.getInstance("PKCS12")
            if(context == null) throw java.lang.Exception("context is null")
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
            val outputStream = FileOutputStream("${context!!.filesDir}${Constants.KEYSTORE_PATH}")
            keyStore!!.store(outputStream, Constants.KEYSTORE_PASSWORD.toCharArray())
            outputStream.close()
            Log.d(TAG, "KEYSTORE WRITTEN TO FILE")
        }

        fun deleteRootCertificateFromKeystore() {
            keyStore!!.deleteEntry("root")
            writeKeyStoreToFile()
        }

        fun deleteKeyStore(context: Context) {
            context.deleteFile("${context.filesDir}${Constants.KEYSTORE_PATH}")
        }

        fun getStoredCertificate(): X509Certificate? {
            return if(keyStore?.getCertificate("root") == null) null
            else{
                keyStore!!.getCertificate("root") as X509Certificate
            }
        }
    }
}
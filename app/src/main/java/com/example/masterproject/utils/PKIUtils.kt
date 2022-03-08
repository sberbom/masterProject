package com.example.masterproject.utils

import android.content.Context
import android.util.Log
import com.example.masterproject.App
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcECContentSignerBuilder
import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


class PKIUtils {

    companion object {
        var privateKey: PrivateKey? = null
        private const val TAG: String = "PKIUtils"
        var CAPublicKey: PublicKey = stringToEncryptionKey("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEi5TBRm/SPUihKiorK4wKzCyOjL4XvG8InzcbePZCtzziYmfGfUEOAImlW76MzYGYOIE+bCg5bo6Fj+p0JBsFgQ==")
        var CACertificate: X509Certificate = stringToCertificate("MIIBGzCBwqADAgECAgYBf2iixnAwCgYIKoZIzj0EAwIwFTETMBEGA1UEAwwKVFRNNDkwNSBDQTAeFw0yMjAzMDcwODI2MTJaFw0yNDAzMDgwODI2MTJaMBUxEzARBgNVBAMMClRUTTQ5MDUgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASLlMFGb9I9SKEqKisrjArMLI6Mvhe8bwifNxt49kK3POJiZ8Z9QQ4AiaVbvozNgZg4gT5sKDlujoWP6nQkGwWBMAoGCCqGSM49BAMCA0gAMEUCIQDsPhn62hQUyRA5kHEQCVlTVQyuRXWQHG8EYPyJ/1EJfQIgZKDrxJKiZROdsW6k8NeF7420C/gOtoQnynC+1ArKU6w=")
        var keyStore: KeyStore? = null
        var trustStore: KeyStore = loadTrustStore()
        private var context: Context? = App.getAppContext()

        fun generateECKeyPair(): KeyPair {
            val ECDSAGenerator = KeyPairGenerator.getInstance(Constants.ASYMMETRIC_KEY_GENERATION_ALGORITHM);
            val keyPair = ECDSAGenerator.genKeyPair()
            privateKey = keyPair.private
            return keyPair
        }

        private fun isValidSelfSignedCertificate(certificate: X509Certificate): Boolean {
            return try {
                certificate.verify(certificate.publicKey)
                true
            } catch (e: Exception) {
                Log.d(TAG, "INVALID SELF SIGN SIGNATURE ON CERTIFICATE")
                e.printStackTrace()
                false
            }
        }

        private fun isValidCACertificate(certificate: X509Certificate): Boolean {
            return try {
                certificate.verify(CAPublicKey)
                true
            } catch (e: Exception) {
                Log.d(TAG, "INVALID CA SIGNATURE ON CERTIFICATE")
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
            val certificateBuilder = X509v3CertificateBuilder(
                certificateIssuer,
                certificateSerialNumber,
                validityBeginDate,
                validityEndDate,
                certificateIssuer,
                publicKeyInfo
            )

            val privateKeyParameters = PrivateKeyFactory.createKey(keyPair.private.encoded)
            val signatureAlgorithm = DefaultSignatureAlgorithmIdentifierFinder().find(Constants.ASYMMETRIC_SIGNATURE_ALGORITHM)
            val digestAlgorithm = DefaultDigestAlgorithmIdentifierFinder().find(signatureAlgorithm)
            val contentSigner = BcECContentSignerBuilder(signatureAlgorithm, digestAlgorithm).build(privateKeyParameters)

            val holder: X509CertificateHolder = certificateBuilder.build(contentSigner)
            return JcaX509CertificateConverter().getCertificate(holder)
        }

            fun isSelfSignedCertificate(certificate: X509Certificate): Boolean {
            return certificate.issuerDN == certificate.subjectDN && isValidSelfSignedCertificate(certificate)
        }

        fun isCASignedCertificate(certificate: X509Certificate): Boolean {
            return certificate.issuerDN.toString() == Constants.CA_CN && isValidCACertificate(
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

            val certificateFactory: CertificateFactory = CertificateFactory.getInstance(Constants.CERTIFICATE_TYPE)
            return certificateFactory.generateCertificate(inputStream) as X509Certificate
        }

        fun stringToEncryptionKey(key: String): PublicKey {
            val encKey = Base64.getDecoder().decode(key)
            val publicKeySpec = X509EncodedKeySpec(encKey)
            val keyFactory = KeyFactory.getInstance(Constants.ASYMMETRIC_KEY_GENERATION_ALGORITHM)
            return keyFactory.generatePublic(publicKeySpec)
        }

        fun encryptionKeyToString(key: PublicKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }

        fun getPrivateKeyFromKeyStore(): PrivateKey? {
            return (keyStore?.getEntry("root", KeyStore.PasswordProtection(Constants.KEYSTORE_PASSWORD.toCharArray())) as KeyStore.PrivateKeyEntry).privateKey
        }

        fun signMessage(message: String, privateKey: PrivateKey, nonce: Int?): String {
            val signatureBuilder = Signature.getInstance(Constants.ASYMMETRIC_SIGNATURE_ALGORITHM)
            signatureBuilder.initSign(privateKey)
            signatureBuilder.update((if (nonce == null) message else "$message:$nonce").toByteArray())
            return Base64.getEncoder().encodeToString(signatureBuilder.sign())
        }

        fun verifySignature(message: String, signature: String, publicKey: PublicKey, nonce: Int?): Boolean {
            val signatureBuilder = Signature.getInstance(Constants.ASYMMETRIC_SIGNATURE_ALGORITHM)
            signatureBuilder.initVerify(publicKey)
            signatureBuilder.update((if (nonce == null) message else "$message:$nonce").toByteArray())
            return signatureBuilder.verify(Base64.getDecoder().decode(signature))

        }

        fun loadKeyStore(): KeyStore {
            val localKeyStore = KeyStore.getInstance(Constants.KEYSTORE_TYPE)
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
            val keyMangerFactory = KeyManagerFactory.getInstance(Constants.KEY_MANAGER_INSTANCE)
            keyMangerFactory.init(keyStore, Constants.KEYSTORE_PASSWORD.toCharArray())
            val keyManagers = keyMangerFactory.keyManagers

            val trustManagerFactory = TrustManagerFactory.getInstance(Constants.KEY_MANAGER_INSTANCE)
            trustManagerFactory.init(trustStore)
            trustStore.setCertificateEntry("CA", CACertificate)
            val trustManagers = trustManagerFactory.trustManagers


            val sslContext = SSLContext.getInstance(Constants.SSL_TYPE)
            sslContext.init(keyManagers, trustManagers, null)

            return sslContext
        }

        fun addKeyToKeyStore(key: PrivateKey, certificate: X509Certificate) {
            deleteRootCertificateFromKeystore()
            Log.d(TAG, "Added key to keystore: $key")
            keyStore!!.setKeyEntry("root", key, Constants.KEYSTORE_PASSWORD.toCharArray(), arrayOf(certificate, CACertificate))
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

        fun getStoredCertificate(): X509Certificate? {
            return if(keyStore?.getCertificate("root") == null) null
            else{
                keyStore!!.getCertificate("root") as X509Certificate
            }
        }
    }
}
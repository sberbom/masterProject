package com.example.masterproject.utils

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import org.bouncycastle.cert.X509v1CertificateBuilder
import java.math.BigInteger
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.FileNotFoundException
import java.lang.Error
import java.net.*
import java.security.spec.PKCS8EncodedKeySpec
import java.io.File
import java.lang.Exception


class PKIUtils {

    companion object {
        var keyPair: KeyPair? = null
        var privateKey: PrivateKey? = null
        private var selfSignedX509Certificate: X509Certificate? = null
        private var CASignedX509Certificate: X509Certificate? = null
        private const val TAG: String = "Utils"
        var CAPublicKey: PublicKey =
            stringToEncryptionKey("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnbbrYnAGkcNYD72o/H7jP2z91bQyA1B8GwUzsV0NG34RhpJ6xJMLxQT0kXwnSunXz6wVndN6O/6ZDWymVCk3nw==")

        fun getCertificate(): X509Certificate? {
            return if (CASignedX509Certificate != null) {
                CASignedX509Certificate
            } else {
                selfSignedX509Certificate
            }
        }

        fun setCertificate(certificate: X509Certificate) {
            if (isCASignedCertificate(certificate)) {
                CASignedX509Certificate = certificate
            } else {
                selfSignedX509Certificate = certificate
            }
        }

        fun generateECKeyPair(): KeyPair {
            val ECDSAGenerator = KeyPairGenerator.getInstance("EC");
            val keyPair = ECDSAGenerator.genKeyPair()
            Companion.keyPair = keyPair
            privateKey = keyPair.private
            return keyPair
        }

        fun storePrivateKey(key: PrivateKey, context: Context) {
            val filename = "my_private_key"
            val fileContents = privateKeyToString(key)
            context.openFileOutput(filename, Context.MODE_PRIVATE).use {
                it.write(fileContents.toByteArray())
            }
        }

        private fun fetchStoredPrivateKey(context: Context): PrivateKey? {
            return try {
                val filename = "my_private_key"
                val fileContent =
                    context.openFileInput(filename).bufferedReader().use { it.readText() }
                val privateKey = stringToPrivateKey(fileContent)
                Companion.privateKey = privateKey
                privateKey
            } catch (e: FileNotFoundException) {
                Log.w("STORED CERTIFICATE", "No stored certificate")
                null
            }
        }

        fun getPrivateKey(context: Context): PrivateKey? {
            return privateKey ?: fetchStoredPrivateKey(context)
        }

        private fun privateKeyToString(key: PrivateKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }

        private fun stringToPrivateKey(string: String): PrivateKey {
            val encKey = Base64.getDecoder().decode(string)
            val privateKeySpec = PKCS8EncodedKeySpec(encKey)
            val keyFactory = KeyFactory.getInstance("EC")
            return keyFactory.generatePrivate(privateKeySpec)
        }

        fun deleteStoredPrivateKey(context: Context) {
            val filename = "my_private_key"
            context.deleteFile(filename)
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
            val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(
                JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
            )

            val holder: X509CertificateHolder = certificateBuilder.build(contentSigner)

            val certificate = JcaX509CertificateConverter().getCertificate(holder)
            selfSignedX509Certificate = certificate
            return certificate
        }

        private fun isValidCACertificate(certificate: X509Certificate): Boolean {
            try {
                certificate.verify(CAPublicKey)
            } catch (e: Error) {
                Log.w("CA Certifiate", e.stackTraceToString())
                return false
            }
            return true
        }

        fun isSelfSignedCertificate(certificate: X509Certificate): Boolean {
            return certificate.issuerDN == certificate.subjectDN
        }

        fun isCASignedCertificate(certificate: X509Certificate): Boolean {
            return certificate.issuerDN.toString() == "CN=TTM4905 CA" && isValidCACertificate(
                certificate
            )
        }

        fun storeCertificate(certificate: X509Certificate, context: Context) {
            val filename = "my_certificate"
            val fileContents = certificateToPem(certificate)
            context.openFileOutput(filename, Context.MODE_PRIVATE).use {
                it.write(fileContents.toByteArray())
            }
        }

        fun fetchStoredCertificate(context: Context): X509Certificate? {
            return try {
                val filename = "my_certificate"
                val fileContent =
                    context.openFileInput(filename).bufferedReader().use { it.readText() }
                val certificate = pemToCertificate(fileContent)
                setCertificate(certificate)
                certificate
            } catch (e: FileNotFoundException) {
                Log.w("STORED CERTIFICATE", "No stored certificate")
                null
            }
        }

        fun deleteStoredCertificate(context: Context) {
            val filename = "my_certificate"
            context.deleteFile(filename)
        }

        private fun certificateToPem(certificate: X509Certificate): String {
            val beginCertificate = "-----BEGIN CERTIFICATE-----";
            val endCertificate = "-----END CERTIFICATE-----";
            val encodedCertificate = Base64.getEncoder().encodeToString(certificate.encoded)
            return "$beginCertificate\n$encodedCertificate\n$endCertificate"
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

        fun getUsernameFromCertificate(certificate: X509Certificate): String {
            return certificate.subjectDN.toString().replace("CN=", "")
        }

        fun encryptMessage(plainText: String, publicKey: PublicKey): String {
            val cipher = Cipher.getInstance("ECIES")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray()))
        }

        fun decryptMessage(encryptedText: String?, privateKey: PrivateKey): String {
            val cipher = Cipher.getInstance("ECIES")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            return String(cipher.doFinal(Base64.getDecoder().decode(encryptedText)))
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

        fun encryptionKeyToPem(key: PublicKey): String {
            val beginCertificate = "-----BEGIN CERTIFICATE-----";
            val endCertificate = "-----END CERTIFICATE-----";
            val encodedKey = Base64.getEncoder().encodeToString(key.encoded)
            return "$beginCertificate\n$encodedKey\n$endCertificate"
        }

        fun encryptionKeyToString(key: PublicKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }

        private fun stringToEncryptionKey(key: String): PublicKey {
            val encKey = Base64.getDecoder().decode(key)
            val publicKeySpec = X509EncodedKeySpec(encKey)
            val keyFactory = KeyFactory.getInstance("EC")
            return keyFactory.generatePublic(publicKeySpec)
        }
    }
}
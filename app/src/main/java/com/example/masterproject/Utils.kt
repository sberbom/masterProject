package com.example.masterproject

import android.content.Context
import android.system.Os.bind
import android.text.TextUtils
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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
import org.bouncycastle.crypto.params.Blake3Parameters.context
import java.io.File
import java.lang.Exception


class Utils {

    companion object {
        var myLedgerEntry: LedgerEntry? = null
        var keyPair: KeyPair? = null
        var privateKey: PrivateKey? = null
        private var selfSignedX509Certificate: X509Certificate? = null
        private var CASignedX509Certificate: X509Certificate? = null
        private const val TAG: String = "Utils"
        var CAPublicKey: PublicKey =
            stringToEncryptionKey("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnbbrYnAGkcNYD72o/H7jP2z91bQyA1B8GwUzsV0NG34RhpJ6xJMLxQT0kXwnSunXz6wVndN6O/6ZDWymVCk3nw==")

        fun getCertificate(): X509Certificate? {
            if (CASignedX509Certificate != null) {
                return CASignedX509Certificate
            } else {
                return selfSignedX509Certificate
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
            Utils.keyPair = keyPair
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

        fun fetchStoredPrivateKey(context: Context): PrivateKey? {
            try {
                val filename = "my_private_key"
                val fileContent =
                    context.openFileInput(filename).bufferedReader().use { it.readText() }
                val privateKey = stringToPrivateKey(fileContent)
                Utils.privateKey = privateKey
                return privateKey
            } catch (e: FileNotFoundException) {
                Log.w("STORED CERTIFICATE", "No stored certificate")
                return null
            }
        }

        fun getPrivateKey(context: Context): PrivateKey? {
            return privateKey ?: fetchStoredPrivateKey(context)
        }

        fun privateKeyToString(key: PrivateKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }

        fun stringToPrivateKey(string: String): PrivateKey {
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
            val validityEndDate = Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000)
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

            val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withECDSA")
            val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
            //val contentSigner: ContentSigner = BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(
            //    PrivateKeyFactory.createKey(keyPair.private.encoded))

            val privateKeyInfo = PrivateKeyInfo.getInstance(keyPair.private.encoded)
            val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(
                JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
            )

            val holder: X509CertificateHolder = certificateBuilder.build(contentSigner)

            val certificate = JcaX509CertificateConverter().getCertificate(holder)
            selfSignedX509Certificate = certificate
            return certificate
        }

        fun isValidCACertificate(certificate: X509Certificate): Boolean {
            try {
                certificate.verify(Utils.CAPublicKey)
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
            try {
                val filename = "my_certificate"
                val fileContent =
                    context.openFileInput(filename).bufferedReader().use { it.readText() }
                val certificate = pemToCertificate(fileContent)
                setCertificate(certificate)
                return certificate
            } catch (e: FileNotFoundException) {
                Log.w("STORED CERTIFICATE", "No stored certificate")
                return null
            }
        }

        fun deleteStoredCertificate(context: Context) {
            val filename = "my_certificate"
            context.deleteFile(filename)
        }

        fun certificateToPem(certificate: X509Certificate): String {
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
                .generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate)!!
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

        fun stringToEncryptionKey(key: String): PublicKey {
            val encKey = Base64.getDecoder().decode(key)
            val publicKeySpec = X509EncodedKeySpec(encKey)
            val keyFactory = KeyFactory.getInstance("EC")
            return keyFactory.generatePublic(publicKeySpec)
        }

        fun getMyIpAddress(): String? {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val networkInterface: NetworkInterface = en.nextElement()
                val enumIpAddress: Enumeration<InetAddress> = networkInterface.inetAddresses
                while (enumIpAddress.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddress.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
            return null
        }

        fun isEmail(email: String): Boolean {
            return !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email)
                .matches();
        }

        fun hashString(input: String): String {
            val bytes = input.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.fold("", { str, it -> str + "%02x".format(it) })
        }

        fun deleteCache(context: Context) {
            try {
                val dir: File = context.cacheDir
                deleteDir(dir)
            }catch (e: Exception) {
                Log.w(TAG, "Could not delete cache")
                e.printStackTrace()
            }
        }

        private fun deleteDir(dir: File?): Boolean {
            return if (dir != null && dir.isDirectory) {
                val children = dir.list()
                if (children != null) {
                    for (i in children.indices) {
                        val success: Boolean = deleteDir(File(dir, children[i]))
                        if (!success) {
                            return false
                        }
                    }
                }
                dir.delete()
            } else if (dir != null && dir.isFile) {
                dir.delete()
            } else {
                false
            }
        }
            
        fun getCurrentUserString(context: Context): String {
            if (Firebase.auth.currentUser != null) {
                return Firebase.auth.currentUser!!.email!!
            }
            else if(fetchStoredCertificate(context) != null){
                return getUsernameFromCertificate(fetchStoredCertificate(context)!!)
            }
            return "Not logged in"
        }

        fun isLoggedIn(context: Context): Boolean {
            return (Firebase.auth.currentUser != null || fetchStoredCertificate(context) != null)
        }
    }
}
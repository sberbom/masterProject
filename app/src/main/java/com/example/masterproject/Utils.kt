package com.example.masterproject

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.lang.Exception
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import org.bouncycastle.cert.X509v1CertificateBuilder
import java.math.BigInteger
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo

import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder




class Utils {

    companion object{
        var keyPair: KeyPair? = null
        var selfSignedX509Certificate: X509Certificate? = null

        private fun generateECKeyPair(): KeyPair {
            val ECDSAGenerator = KeyPairGenerator.getInstance("EC");
            val keyPair = ECDSAGenerator.genKeyPair()
            Utils.keyPair  = keyPair
            return keyPair
        }

        //https://www.bouncycastle.org/docs/pkixdocs1.5on/org/bouncycastle/cert/X509v1CertificateBuilder.html
        fun generateSelfSignedX509Certificate(): X509Certificate  {
            val keyPair = generateECKeyPair()
            val validityBeginDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            val validityEndDate = Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000)
            val certificateIssuer = X500Name("CN=SelfSigned")
            val certificateSerialNumber = BigInteger.valueOf(System.currentTimeMillis())
            val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded);
            val certificateBuilder = X509v1CertificateBuilder(certificateIssuer, certificateSerialNumber, validityBeginDate, validityEndDate, certificateIssuer, publicKeyInfo)

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
            try {
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
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}
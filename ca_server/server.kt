package function;

import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcECContentSignerBuilder
import java.io.BufferedWriter
import java.io.IOException
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.logging.Logger;


class  Server: HttpFunction {

    private val privateKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg6IRKAAyFsgn3UIULW90B7QalGyvvD/aFBKz34i07FcWhRANCAASLlMFGb9I9SKEqKisrjArMLI6Mvhe8bwifNxt49kK3POJiZ8Z9QQ4AiaVbvozNgZg4gT5sKDlujoWP6nQkGwWB"
    private val publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEi5TBRm/SPUihKiorK4wKzCyOjL4XvG8InzcbePZCtzziYmfGfUEOAImlW76MzYGYOIE+bCg5bo6Fj+p0JBsFgQ=="
    private val ASYMMETRIC_SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private val ASYMMETRIC_KEY_GENERATION_ALGORITHM = "EC"
    private val gson = Gson()

    @Throws(IOException::class)
    override fun service(request: HttpRequest, response: HttpResponse) {

        val body: JsonObject = gson.fromJson(request.getReader(), JsonObject::class.java)
        val email = body.get("email").asString
        val keyString = body.get("publicKeyString").asString
        val key = stringToPublic(keyString)
        val logger: Logger = Logger.getLogger(Server::class.java.name)


        val certificate = generateSignedX509Certificate(email, key)
        val certificateString = Base64.getEncoder().encodeToString(certificate.encoded)

        val writer: BufferedWriter = response.getWriter()
        writer.write(certificateString)
    }

    private fun generateSignedX509Certificate(username: String, publicKey: PublicKey): X509Certificate {
        val validityBeginDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val validityEndDate = addMonth(Date(), 1)
        val certificateIssuer = X500Name("CN=TTM4905 CA")
        val certificateSubject= X500Name("CN=$username")
        val certificateSerialNumber = BigInteger.valueOf(System.currentTimeMillis())
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.encoded);
        val certificateBuilder = X509v3CertificateBuilder(
            certificateIssuer,
            certificateSerialNumber,
            validityBeginDate,
            validityEndDate,
            certificateSubject,
            publicKeyInfo
        )

        val privateKey = stringToPrivate(privateKey)
        val privateKeyParameters = PrivateKeyFactory.createKey(privateKey.encoded)
        val signatureAlgorithm =
            DefaultSignatureAlgorithmIdentifierFinder().find(ASYMMETRIC_SIGNATURE_ALGORITHM)
        val digestAlgorithm = DefaultDigestAlgorithmIdentifierFinder().find(signatureAlgorithm)
        val contentSigner = BcECContentSignerBuilder(
            signatureAlgorithm,
            digestAlgorithm
        ).build(privateKeyParameters)

        val holder: X509CertificateHolder = certificateBuilder.build(contentSigner)
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private fun stringToPrivate(key: String): PrivateKey{
        val encKey = Base64.getDecoder().decode(key)
        val privateKeySpec = PKCS8EncodedKeySpec(encKey)
        val keyFactory = KeyFactory.getInstance(ASYMMETRIC_KEY_GENERATION_ALGORITHM)
        return keyFactory.generatePrivate(privateKeySpec)
    }

    private fun stringToPublic (key: String): PublicKey {
        val encKey = Base64.getDecoder().decode(key)
        val publicKeySpec = X509EncodedKeySpec(encKey)
        val keyFactory = KeyFactory.getInstance(ASYMMETRIC_KEY_GENERATION_ALGORITHM)
        return keyFactory.generatePublic(publicKeySpec)
    }

    private fun addMonth(date: Date, i: Int): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.MONTH, i)
        return cal.time
    }
}
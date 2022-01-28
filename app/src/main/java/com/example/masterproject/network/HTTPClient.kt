package com.example.masterproject.network

import android.content.Context
import android.util.Log
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.masterproject.utils.PKIUtils
import org.json.JSONObject
import java.lang.Exception
import java.nio.charset.Charset

class HTTPClient(private val email: String, private val context: Context): Runnable{

    override fun run() {
        try{
            getCASignedCertificate(email, context)
        }
        catch (error: Exception) {
            error.printStackTrace()
        }
    }

    private fun getCASignedCertificate(email: String, context: Context) {
        val keyPair = PKIUtils.generateECKeyPair()
        PKIUtils.storePrivateKey(keyPair.private, context)
        PKIUtils.privateKey = keyPair.private
        val url = "https://europe-west1-master-project-337221.cloudfunctions.net/x509Certificate"
        val queue = Volley.newRequestQueue(context)

        val requestBody = JSONObject().put("email", email).put("publicKeyString",
            PKIUtils.encryptionKeyToPem(keyPair.public)
        ).toString()
        val stringReq: StringRequest =
            object : StringRequest(Method.POST, url,
                Response.Listener { response ->
                    var strResp = response.toString()
                    val certificate = PKIUtils.pemToCertificate(strResp)
                    PKIUtils.setCertificate(certificate)
                    PKIUtils.storeCertificate(certificate, context)
                },
                Response.ErrorListener { error ->
                    Log.d("SIGMUND API", "error => $error")
                }
            ) {
                override fun getBody(): ByteArray {
                    return requestBody.toByteArray(Charset.defaultCharset())
                }
            }
        queue.add(stringReq)
    }
}
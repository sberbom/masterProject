package com.example.masterproject

import android.content.Context
import android.util.Log
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.lang.Exception
import java.nio.charset.Charset
import java.security.KeyPair

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
        val keyPair = Utils.generateECKeyPair()
        Utils.storePrivateKey(keyPair.private, context)
        Utils.privateKey = keyPair.private
        val url = "https://europe-west1-master-project-337221.cloudfunctions.net/x509Certificate"
        val queue = Volley.newRequestQueue(context)

        val requestBody = JSONObject().put("email", email).put("publicKeyString", Utils.encryptionKeyToPem(keyPair.public)).toString()
        val stringReq: StringRequest =
            object : StringRequest(Method.POST, url,
                Response.Listener { response ->
                    // response
                    var strResp = response.toString()
                    val certificate = Utils.pemToCertificate(strResp)
                    Utils.setCertificate(certificate)
                    Utils.storeCertificate(certificate, context)
                    //Utils.myLedgerEntry = LedgerEntry(certificate, Utils.getUsernameFromCertificate(certificate))

                    //Log.d("SIGMUND API", strResp)
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
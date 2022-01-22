package com.example.masterproject

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.masterproject.Ledger.Companion.availableDevices
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import android.content.Intent
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase


class MainActivity: AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val multicastGroup: String = "224.0.0.10"
    private val multicastPort: Int = 8888
    private val multicastServerThread = MulticastServer(multicastGroup, multicastPort)
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        auth = Firebase.auth

        //Set up view
        recyclerView = findViewById(R.id.recyclerView)
        var myAdapter = DeviceAdapter(availableDevices.toTypedArray(),this)
        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        //Create my ledger entry
        val storedCertificate = Utils.fetchStoredCertificate(this)
        var username = "user-${(0..100).random()}"
        if(storedCertificate == null) {
            val keyPair = Utils.generateECKeyPair()
            Utils.storePrivateKey(keyPair.private, this)
            val certificate = Utils.generateSelfSignedX509Certificate(username, keyPair)
            Utils.storeCertificate(certificate, this)
        }
        else {
            username = Utils.getUsernameFromCertificate(storedCertificate)
            Utils.fetchStoredPrivateKey(this)
        }
        Utils.myLedgerEntry = LedgerEntry(Utils.getCertificate()!!, username)

        //Start network processes
        val multicastClientThread = MulticastClient(multicastGroup, multicastPort)
        Thread(multicastServerThread).start()
        Thread(multicastClientThread).start()
        val tcpServerThread = TCPServer(this)
        Thread(tcpServerThread).start()

        //Find and display my IP address
        val myIpAddressTextView: TextView = findViewById(R.id.myIpAddress)
        val myIpAddressText = "My IP address: ${Utils.getMyIpAddress()}"
        myIpAddressTextView.text = myIpAddressText


        //Setup available devices button and display
        val updateAvailableDevicesButton: Button = findViewById(R.id.updateaAvailableDevicesButton)
        updateAvailableDevicesButton.setOnClickListener {
            recyclerView = findViewById(R.id.recyclerView)
            myAdapter = DeviceAdapter(availableDevices.toTypedArray(), this)
            recyclerView.adapter = myAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        //Sign up button
        val signUpButton: Button = findViewById(R.id.signUpButton)
        signUpButton.setOnClickListener{
            val myIntent = Intent(this@MainActivity, SignUpActivity::class.java)
            //myIntent.putExtra("key", value) //Optional parameters
            this@MainActivity.startActivity(myIntent)
        }

        //Log in button
        val logInButton: Button = findViewById(R.id.logInButton)
        logInButton.setOnClickListener{
            val myIntent = Intent(this@MainActivity, LogInActivity::class.java)
            //myIntent.putExtra("key", value) //Optional parameters
            this@MainActivity.startActivity(myIntent)
        }

        //Log out
        val logOutButton: Button = findViewById(R.id.logOutButton)
        logOutButton.setOnClickListener {
            if(auth.currentUser != null) {
                auth.signOut()
                val loggedInAsText: TextView = findViewById(R.id.loggedInAsText)
                loggedInAsText.text = "Logged in as: Not logged in"
            }
            Toast.makeText(baseContext, "Logged out",
                Toast.LENGTH_SHORT).show()
        }

        //Logged in as text
        if(auth.currentUser != null) {
            val loggedInAsText: TextView = findViewById(R.id.loggedInAsText)
            loggedInAsText.text = "Logged in as: ${auth.currentUser!!.email}"
        }

        //Delete button
        val deleteDataButton: Button = findViewById(R.id.deleteButton)
        deleteDataButton.setOnClickListener {
            Utils.deleteStoredCertificate(this)
            Utils.deleteStoredPrivateKey(this)
            AESUtils.deleteAllStoredKeys(this)
            Toast.makeText(baseContext, "Stored certificate and private key deleted",
                Toast.LENGTH_SHORT).show()
        }

    }

}
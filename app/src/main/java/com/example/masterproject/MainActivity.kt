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

class MainActivity: AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val multicastGroup: String = "224.0.0.10"
    private val multicastPort: Int = 8888
    private val multicastServerThread = MulticastServer(multicastGroup, multicastPort)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        //Set up view
        recyclerView = findViewById(R.id.recyclerView)
        var myAdapter = DeviceAdapter(availableDevices.toTypedArray(),"")
        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        //Create my ledger entry
        Utils.generateSelfSignedX509Certificate()
        val username = "user-${(0..100).random()}"
        val myLedgerEntry = LedgerEntry(Utils.selfSignedX509Certificate!!, username)

        //Start network processes
        val multicastClientThread = MulticastClient(multicastGroup, multicastPort, myLedgerEntry)
        Thread(multicastServerThread).start()
        Thread(multicastClientThread).start()
        val tcpServerThread = TCPServer(findViewById(R.id.tcpMessage))
        Thread(tcpServerThread).start()

        //Find and display my IP address
        val myIpAddressTextView: TextView = findViewById(R.id.myIpAddress)
        val myIpAddressText = "My IP address: ${Utils.getMyIpAddress()}"
        myIpAddressTextView.text = myIpAddressText

        //Setup edit text field
        val messageEditText: EditText = findViewById(R.id.messageText)
        val messageText = messageEditText.text.toString()
        messageEditText.doAfterTextChanged {
            recyclerView = findViewById(R.id.recyclerView)
            myAdapter = DeviceAdapter(availableDevices.toTypedArray(), messageEditText.text.toString())
            recyclerView.adapter = myAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        //Setup available devices button and display
        val updateAvailableDevicesButton: Button = findViewById(R.id.updateaAvailableDevicesButton)
        updateAvailableDevicesButton.setOnClickListener {
            recyclerView = findViewById(R.id.recyclerView)
            myAdapter = DeviceAdapter(availableDevices.toTypedArray(), messageText)
            recyclerView.adapter = myAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
        }


    }

}
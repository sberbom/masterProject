package com.example.masterproject

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity: AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val multicastGroup: String = "224.0.0.10"
    private val multicastPort: Int = 8888
    private val multicastServerThread = MulticastServer(multicastGroup, multicastPort)
    private val multicastClientThread = MulitcastClient(multicastGroup, multicastPort)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        val myAdapter = DeviceAdapter(getAvailableDevices(),"")
        recyclerView.adapter = myAdapter;
        recyclerView.layoutManager = LinearLayoutManager(this);

        Thread(multicastServerThread).start()
        Thread(multicastClientThread).start()
        val tcpServerThread = TCPServer(findViewById(R.id.tcpMessage));
        Thread(tcpServerThread).start()

        val myIpAddressTextView: TextView = findViewById(R.id.myIpAddress)
        val myIpAddressText = "My IP address: ${multicastClientThread.getMyIpAddress()}"
        myIpAddressTextView.text = myIpAddressText

        val messageEditText: EditText = findViewById(R.id.messageText)
        val messageText = messageEditText.text.toString()
        messageEditText.doAfterTextChanged {
            recyclerView = findViewById(R.id.recyclerView)
            val myAdapter = DeviceAdapter(getAvailableDevices(), messageEditText.text.toString())
            recyclerView.adapter = myAdapter;
            recyclerView.layoutManager = LinearLayoutManager(this);
        }


        val updateAvailableDevicesButton: Button = findViewById(R.id.updateaAvailableDevicesButton)
        updateAvailableDevicesButton.setOnClickListener {
            recyclerView = findViewById(R.id.recyclerView)
            val myAdapter = DeviceAdapter(getAvailableDevices(), messageText)
            recyclerView.adapter = myAdapter;
            recyclerView.layoutManager = LinearLayoutManager(this);
        }
    }

    private fun getAvailableDevices(): Array<String> {
        return multicastServerThread.availableDevices.toTypedArray()
    }

}
package com.example.masterproject

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ChatActivity: AppCompatActivity() {

    private val tcpClient = TCPClient()
    private val TAG = "ChatActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        val userName = intent.getStringExtra("userName")
        val isStartingConnection = intent.getBooleanExtra("staringNewConnection", false)
        val ledgerEntry = Ledger.getLedgerEntry(userName!!)

        //NEXT KEY OR MAKE NEW KEY
        AESUtils.currentKey = AESUtils.getEncryptionKey(userName, this)
        Log.d(TAG, "CURRENT KEY SET")
        val currentKeyText: TextView = findViewById(R.id.currentKeyText)
        currentKeyText.text = String(AESUtils.currentKey!!.encoded)

        //MAKE NEXT KEY, STORE AND SEND NEXT KEY
        if(isStartingConnection){
            AESUtils.nextKey = AESUtils.generateAESKey()
            val nextKeyText: TextView = findViewById(R.id.nextEncryptionKey)
            nextKeyText.text = "${String(AESUtils.nextKey!!.encoded)} - starting converstation"
            //AESUtils.storeAESKey(nextKey, ledgerEntry!!.userName, this)
            val nextKeyString = AESUtils.keyToString(AESUtils.nextKey!!)
            GlobalScope.launch(Dispatchers.IO) {
                tcpClient.sendMessage(ledgerEntry!!, "${Constants.KEY_DELEVERY}:://${AESUtils.symmetricEncryption(nextKeyString, AESUtils.currentKey!!)}")
            }
        }
        else {
            val nextKeyText: TextView = findViewById(R.id.nextEncryptionKey)
            nextKeyText.text = "${String(AESUtils.nextKey!!.encoded)} - received"
        }


        val tcpServerThread = TCPServer(this)
        Thread(tcpServerThread).start()

        val messageView: RecyclerView = findViewById(R.id.chatView)
        messageView.adapter = chatAdapter
        messageView.layoutManager = LinearLayoutManager(this)

        val youAreChattingWith: TextView = findViewById(R.id.chatWith)
        youAreChattingWith.text = "You are chatting with: $userName"

        val sendMessageButton: Button = findViewById(R.id.messageSendButton)
        sendMessageButton.setOnClickListener {
            val messageEditText: EditText = findViewById(R.id.messageEditText)
            val messageText = messageEditText.text.toString()
            GlobalScope.launch(Dispatchers.IO) {
                tcpClient.sendEncryptedMessage(ledgerEntry!!, messageText, AESUtils.currentKey!!)
            }
        }
    }

    override fun onStop() {
        messages.clear()
        AESUtils.currentKey = AESUtils.nextKey
        AESUtils.nextKey = null
        super.onStop()
    }

    companion object {

        var messages: MutableList<ChatMessage> = mutableListOf();
        var chatAdapter = ChatAdapter(messages)


        fun addChat(userName: String, msg: String) {
            messages.add(ChatMessage(userName, msg))
            chatAdapter.notifyDataSetChanged()
        }
    }
}
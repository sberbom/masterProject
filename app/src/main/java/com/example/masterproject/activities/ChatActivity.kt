package com.example.masterproject.activities

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.masterproject.R
import com.example.masterproject.activities.adapters.ChatAdapter
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.network.unicast.*
import com.example.masterproject.types.ChatMessage
import com.example.masterproject.utils.AESUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ChatActivity: AppCompatActivity() {

    private var username: String? = null
    private var client: Client? = null
    private var server: Server? = null
    private var clientThread: Thread? = null
    private val TAG ="ChatActivity"
    private var isClient = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        username = intent.getStringExtra("userName")
        isClient = intent.getBooleanExtra("isClient", true)
        val ledgerEntry = Ledger.getLedgerEntry(username!!)

        startServerOrClient(ledgerEntry!!)

        setUpUI()
    }

    private fun startServerOrClient(ledgerEntry: LedgerEntry) {
        if(ServerMap.serverMap.containsKey(username)){
            server = ServerMap.serverMap[username]
            isClient = false
        }
        else {
            client = if(AESUtils.getCurrentKeyForUser(username!!) == null) {
                TLSClient(ledgerEntry)
            } else {
                TCPClient(ledgerEntry)
            }
            clientThread = Thread(client as Thread)
            clientThread!!.start()
        }
    }

    private fun setUpUI(){
        messageView = findViewById(R.id.chatView)
        messageView.adapter = chatAdapter
        messageView.layoutManager = LinearLayoutManager(this)

        val youAreChattingWith: TextView = findViewById(R.id.chatWith)
        val youAreChattingWithText = "You are chatting with: $username"
        youAreChattingWith.text = youAreChattingWithText

        val currentKey = AESUtils.getEncryptionKey(username!!, this)
        val currentKeyText: TextView = findViewById(R.id.currentKeyText)
        currentKeyText.text = AESUtils.keyToString(currentKey)

        val sendMessageButton: ImageView = findViewById(R.id.messageSendButton)
        sendMessageButton.setOnClickListener {
            val messageEditText: EditText = findViewById(R.id.messageEditText)
            val messageText = messageEditText.text.toString()
            sendMessage(messageText, UnicastMessageTypes.CHAT_MESSAGE.toString())
        }
    }

    private fun sendMessage(messageText: String, messageType: String) {
        if(isClient) {
            GlobalScope.launch(Dispatchers.IO) {
                client!!.sendMessage(messageText, messageType)
            }
        }
        else {
            GlobalScope.launch(Dispatchers.IO) {
                server!!.sendMessage(messageText, messageType)
            }
        }
    }

    override fun onStop() {
        messages.clear()
        AESUtils.useNextKeyForUser(username!!)
        if(isClient){
            sendMessage("", UnicastMessageTypes.GOODBYE.toString())
            GlobalScope.launch(Dispatchers.IO) {
                client!!.closeSocket()
            }
            client!!.interrupt()
        }
        super.onStop()
    }

    companion object {

        var messages: MutableList<ChatMessage> = mutableListOf();
        var chatAdapter = ChatAdapter(messages)
        lateinit var messageView: RecyclerView


        fun addChat(userName: String, msg: String) {
            messages.add(ChatMessage(userName, msg))
            chatAdapter.notifyDataSetChanged()
            messageView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}
package com.example.masterproject

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

class ChatActivity: AppCompatActivity() {

    private var userName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        userName = intent.getStringExtra("userName")
        val isStartingConnection = intent.getBooleanExtra("staringNewConnection", false)
        val ledgerEntry = Ledger.getLedgerEntry(userName!!)

        val currentKey = AESUtils.getEncryptionKey(userName!!, this)
        updateAESKeys(userName!!, isStartingConnection, ledgerEntry!!, currentKey)

        messageView = findViewById(R.id.chatView)
        messageView.adapter = chatAdapter
        messageView.layoutManager = LinearLayoutManager(this)

        val youAreChattingWith: TextView = findViewById(R.id.chatWith)
        val youAreChattingWithText = "You are chatting with: $userName"
        youAreChattingWith.text = youAreChattingWithText

        val currentKeyText: TextView = findViewById(R.id.currentKeyText)
        currentKeyText.text = String(currentKey.encoded)

        val sendMessageButton: ImageView = findViewById(R.id.messageSendButton)
        sendMessageButton.setOnClickListener {
            val messageEditText: EditText = findViewById(R.id.messageEditText)
            val messageText = messageEditText.text.toString()
            GlobalScope.launch(Dispatchers.IO) {
                TCPClient.sendEncryptedMessage(ledgerEntry!!, messageText, currentKey)
            }
        }
    }

    override fun onStop() {
        messages.clear()
        AESUtils.useNextKeyForUser(userName!!)
        super.onStop()
    }

    private fun updateAESKeys(userName: String, isStartingConnection: Boolean, ledgerEntry: LedgerEntry, currentKey: SecretKey) {
        if(isStartingConnection) {
            val nextKey = AESUtils.generateAESKey()
            AESUtils.setNextKeyForUser(userName, nextKey)
            GlobalScope.launch(Dispatchers.IO) {
                TCPClient.sendKeyDelivery(ledgerEntry, nextKey, currentKey)
            }
        }
        val nextKey = AESUtils.getNextKeyForUser(userName)?.encoded?.let { String(it) } ?: "No next key stored"
        val nextKeyTextView: TextView = findViewById(R.id.nextEncryptionKey)
        val nextKeyText = if(isStartingConnection) "$nextKey - sent" else "$nextKey - received"
        nextKeyTextView.text = nextKeyText
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
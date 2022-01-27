package com.example.masterproject.activities.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.masterproject.types.ChatMessage
import com.example.masterproject.R

class ChatAdapter(private val messages: MutableList<ChatMessage>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val YOU = 0
    private val OTHER = 1

    class YouChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val fromTextView: TextView = itemView.findViewById(R.id.chatFromYou)
        val messageTextVew: TextView = itemView.findViewById(R.id.chatMessageYou)
    }

    class OtherChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val fromTextView: TextView = itemView.findViewById(R.id.chatFrom)
        val messageTextVew: TextView = itemView.findViewById(R.id.chatMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):RecyclerView.ViewHolder {
        val viewHolder: RecyclerView.ViewHolder
        val inflater = LayoutInflater.from(parent.context)
        when (viewType) {
            YOU -> {
                val v1: View = inflater.inflate(R.layout.you_chat_message, parent, false)
                viewHolder = YouChatViewHolder(v1)
            }
            else -> {
                val v2: View = inflater.inflate(R.layout.other_chat_message, parent, false)
                viewHolder = OtherChatViewHolder(v2)
            }
        }
        return viewHolder

    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatMessage = messages[position]
        when (holder.itemViewType) {
            YOU -> {
                val vh1 = holder as YouChatViewHolder
                vh1.fromTextView.text = chatMessage.from
                vh1.messageTextVew.text = chatMessage.message
            }
            else -> {
                val vh2 = holder as OtherChatViewHolder
                vh2.fromTextView.text = chatMessage.from
                vh2.messageTextVew.text = chatMessage.message
            }
        }

    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].from == "You:") {
            YOU
        } else{
            OTHER
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

}
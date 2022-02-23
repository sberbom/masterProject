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
        val inflater = LayoutInflater.from(parent.context)
        val viewHolder = when (viewType) {
            YOU -> {
                val v1: View = inflater.inflate(R.layout.you_chat_message, parent, false)
                YouChatViewHolder(v1)
            }
            else -> {
                val v2: View = inflater.inflate(R.layout.other_chat_message, parent, false)
                OtherChatViewHolder(v2)
            }
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatMessage = messages[position]
        var shouldRenderFromText = true
        if(position != 0) {
            val previousChatMessage = messages[position - 1]
            shouldRenderFromText = chatMessage.from != previousChatMessage.from
        }
        when (holder.itemViewType) {
            YOU -> {
                val vh1 = holder as YouChatViewHolder
                if(shouldRenderFromText) {
                    vh1.fromTextView.text = chatMessage.from
                }
                else {
                    vh1.fromTextView.visibility = View.GONE
                }
                vh1.messageTextVew.text = chatMessage.message
            }
            else -> {
                val vh2 = holder as OtherChatViewHolder
                if(shouldRenderFromText) {
                    vh2.fromTextView.text = chatMessage.from
                }
                else {
                    vh2.fromTextView.visibility = View.GONE
                }
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
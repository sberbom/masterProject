package com.example.masterproject.activities.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.masterproject.App
import com.example.masterproject.R
import com.example.masterproject.utils.PKIUtils
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.network.TCPClient
import com.example.masterproject.network.UnicastMessageTypes
import com.example.masterproject.utils.MISCUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DeviceAdapter(private val ledger: MutableList<LedgerEntry>): RecyclerView.Adapter<RecyclerView.ViewHolder>()  {

    private val context = App.getAppContext()

    private val SHOW = 0
    private val HIDE = 1

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val usernameTextView: TextView = itemView.findViewById(R.id.usernameText)
        val ipTextView: TextView = itemView.findViewById(R.id.ipText)
        val publicKeyTextView: TextView = itemView.findViewById(R.id.publicKeyText)
        val startChatButton: Button = itemView.findViewById(R.id.startChatButton)
        val certificateIndication: ImageView = itemView.findViewById(R.id.certificateIndication)
    }

    class EmptyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val viewHolder = when (viewType) {
            SHOW -> {
                val v1: View = inflater.inflate(R.layout.device_info, parent, false)
                DeviceViewHolder(v1)
            }
            else -> {
                val v2: View = inflater.inflate(R.layout.empty, parent, false)
                EmptyViewHolder(v2)
            }
        }
        return viewHolder;
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ledgerEntry = ledger[position]
        if (holder is DeviceViewHolder) {
            holder.usernameTextView.text = ledgerEntry.userName
            holder.ipTextView.text = ledgerEntry.ipAddress
            holder.publicKeyTextView.text = "Certificate hash: ${ledgerEntry.certificate.hashCode()}"
            if(PKIUtils.isCASignedCertificate(ledgerEntry.certificate)){
                holder.certificateIndication.setImageResource(R.drawable.green)
            }
            else if(PKIUtils.isSelfSignedCertificate(ledgerEntry.certificate)){
                holder.certificateIndication.setImageResource(R.drawable.yellow)
            }else {
                holder.certificateIndication.setImageResource(R.drawable.red)
            }
            holder.startChatButton.setOnClickListener {
                if(context != null && MISCUtils.isLoggedIn()){
                    val intent = Intent(context, ChatActivity::class.java)
                    intent.putExtra("userName", ledgerEntry.userName) //Optional parameters
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.putExtra("staringNewConnection", true)
                    context.startActivity(intent)
                    GlobalScope.launch(Dispatchers.IO) {
                        TCPClient.sendMessage(ledgerEntry, UnicastMessageTypes.CLIENT_HELLO.toString())
                    }
                } else {
                    Toast.makeText(
                        context, "Please log in or register before sending messages",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }
        }
    }

    override fun getItemCount(): Int {
        return ledger.size;
    }

    override fun getItemViewType(position: Int): Int {
        return if (Ledger.shouldBeRendered(ledger[position])) {
            SHOW
        } else{
            HIDE
        }
    }
}
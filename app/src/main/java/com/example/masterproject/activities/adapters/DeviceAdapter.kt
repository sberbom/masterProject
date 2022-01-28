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
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.network.TCPClient
import com.example.masterproject.network.UnicastMessageTypes
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DeviceAdapter(private val s1: MutableList<LedgerEntry>): RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>()  {

    private val context = App.getAppContext()

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val usernameTextView: TextView = itemView.findViewById(R.id.usernameText)
        val ipTextView: TextView = itemView.findViewById(R.id.ipText)
        val publicKeyTextView: TextView = itemView.findViewById(R.id.publicKeyText)
        val startChatButton: Button = itemView.findViewById(R.id.startChatButton)
        val certificateIndication: ImageView = itemView.findViewById(R.id.certificateIndication)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val inflater: LayoutInflater = LayoutInflater.from(parent.context)
        val view: View = inflater.inflate(R.layout.device_info, parent, false)
        return DeviceViewHolder(view);
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val ledgerEntry = s1[position]
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
            if(context != null && MISCUtils.isLoggedIn(context)){
                val intent = Intent(context, ChatActivity::class.java)
                intent.putExtra("userName", ledgerEntry.userName) //Optional parameters
                intent.putExtra("staringNewConnection", true)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK;
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

    override fun getItemCount(): Int {
        return s1.size;
    }
}
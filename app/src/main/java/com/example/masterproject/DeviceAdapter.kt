package com.example.masterproject

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DeviceAdapter(private val s1: Array<LedgerEntry>, private val context: Context): RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>()  {

    private val tcpClient = TCPClient()

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
        holder.ipTextView.text = "IP-address: ${ledgerEntry.ipAddress}"
        holder.publicKeyTextView.text = "Certificate hash: ${ledgerEntry.certificate.hashCode()}"
        if(Utils.isCASignedCertificate(ledgerEntry.certificate)){
            holder.certificateIndication.setImageResource(R.drawable.green)
        }
        else if(Utils.isSelfSignedCertificate(ledgerEntry.certificate)){
            holder.certificateIndication.setImageResource(R.drawable.yellow)
        }else {
            holder.certificateIndication.setImageResource(R.drawable.red)
        }
        holder.startChatButton.setOnClickListener {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("userName", ledgerEntry.userName) //Optional parameters
            intent.putExtra("staringNewConnection", true)
            context.startActivity(intent)
            GlobalScope.launch(Dispatchers.IO) {
                TCPClient.sendMessage(ledgerEntry, Constants.CLIENT_HELLO)
            }
        }
    }

    override fun getItemCount(): Int {
        return s1.size;
    }
}
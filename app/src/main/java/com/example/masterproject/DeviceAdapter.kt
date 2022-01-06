package com.example.masterproject

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val s1: Array<LedgerEntry>, private val message: String): RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>()  {


    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val usernameTextView: TextView = itemView.findViewById(R.id.usernameText)
        val ipTextView: TextView = itemView.findViewById(R.id.ipText)
        val publicKeyTextView: TextView = itemView.findViewById(R.id.publicKeyText)
        val sendButton: Button = itemView.findViewById(R.id.sendButton)
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
        holder.sendButton.setOnClickListener {
            val TCPClientThred = TCPClient(ledgerEntry.userName, message)
            Thread(TCPClientThred).start()
        }
    }

    override fun getItemCount(): Int {
        return s1.size;
    }
}
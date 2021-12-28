package com.example.masterproject

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val s1: Array<String>): RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>()  {


    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val deviceIp: TextView = itemView.findViewById(R.id.textView3)
        val sendButton: Button = itemView.findViewById(R.id.sendButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val inflater: LayoutInflater = LayoutInflater.from(parent.context)
        val view: View = inflater.inflate(R.layout.device_info, parent, false)
        return DeviceViewHolder(view);
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val ip = s1[position]
        holder.deviceIp.text = "$ip"

        holder.sendButton.setOnClickListener {
            val TCPClientThred = TCPClient(ip)
            Thread(TCPClientThred).start()
        }
    }

    override fun getItemCount(): Int {
        return s1.size;
    }
}
package com.example.masterproject.types

import org.json.JSONObject

data class UnicastPacket(val sender: String, val payload: String, val messageType: String, val ratchetKey: Int = -1) {
    override fun toString(): String {
        val jsonObject = JSONObject()
        jsonObject.put("sender", sender)
        jsonObject.put("payload", payload)
        jsonObject.put("mt", messageType)
        jsonObject.put("rk", ratchetKey)
        return jsonObject.toString()
    }

    companion object {
        fun decodeUnicastPacket(string: String): UnicastPacket {
            val jsonObject = JSONObject(string)
            val sender = jsonObject.getString("sender")
            val payload = jsonObject.getString("payload")
            val messageType = jsonObject.getString("mt") 
            val ratchetKey = jsonObject.getInt("rk")
            return UnicastPacket(sender, payload, messageType, ratchetKey)
        }
    }
}
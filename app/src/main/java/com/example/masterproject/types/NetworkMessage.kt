package com.example.masterproject.types

import org.json.JSONObject

data class NetworkMessage(val sender: String, val payload: String, val messageType: String, val signature: String = "") {
    override fun toString(): String {
        val jsonObject = JSONObject()
        jsonObject.put("sender", sender)
        jsonObject.put("payload", payload)
        jsonObject.put("messageType", messageType)
        jsonObject.put("signature", signature)
        return jsonObject.toString()
    }

    companion object {
        fun decodeNetworkMessage(string: String): NetworkMessage {
            val jsonObject = JSONObject(string)
            val sender = jsonObject.get("sender") as String
            val payload = jsonObject.get("payload") as String
            val messageType = jsonObject.get("messageType") as String
            val signature = jsonObject.get("signature") as String
            return NetworkMessage(sender, payload, messageType, signature)
        }
    }
}
package com.example.masterproject.types

import org.json.JSONObject

data class MulticastPacket(val sender: String, val payload: String, val messageType: String, val signature: String = "", val nonce: Int = 0, val sequenceNumber: Int = 0, val lastSequenceNumber: Int = 0) {
    override fun toString(): String {
        val jsonObject = JSONObject()
        jsonObject.put("sender", sender)
        jsonObject.put("payload", payload)
        jsonObject.put("mt", messageType)
        jsonObject.put("sig", signature)
        jsonObject.put("nonce", nonce)
        jsonObject.put("seq", sequenceNumber)
        jsonObject.put("tot", lastSequenceNumber)
        return jsonObject.toString()
    }

    companion object {
        fun decodeMulticastPacket(string: String): MulticastPacket {
            val jsonObject = JSONObject(string)
            val sender = jsonObject.get("sender") as String
            val payload = jsonObject.get("payload") as String
            val messageType = jsonObject.get("mt") as String
            val signature = jsonObject.get("sig") as String
            val nonce = jsonObject.get("nonce") as Int
            val sequenceNumber = jsonObject.get("seq") as Int
            val lastSequenceNumber = jsonObject.get("tot") as Int
            return MulticastPacket(sender, payload, messageType, signature, nonce, sequenceNumber, lastSequenceNumber)
        }
    }
}
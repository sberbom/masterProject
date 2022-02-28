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
            val sender = jsonObject.getString("sender")
            val payload = jsonObject.getString("payload")
            val messageType = jsonObject.getString("mt")
            val signature = jsonObject.getString("sig")
            val nonce = jsonObject.getInt("nonce")
            val sequenceNumber = jsonObject.getInt("seq")
            val lastSequenceNumber = jsonObject.getInt("tot")
            return MulticastPacket(sender, payload, messageType, signature, nonce, sequenceNumber, lastSequenceNumber)
        }
    }
}
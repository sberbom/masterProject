package com.example.masterproject.types

import org.json.JSONObject

data class NetworkMessage(val sender: String, val payload: String, val messageType: String, val signature: String = "", val nonce: Int = 0, val sequenceNumber: Int = 0, val lastSequenceNumber: Int = 0, val ratchetKey: Int = -1) {
    override fun toString(): String {
        val jsonObject = JSONObject()
        jsonObject.put("sender", sender)
        jsonObject.put("payload", payload)
        jsonObject.put("messageType", messageType)
        jsonObject.put("signature", signature)
        jsonObject.put("nonce", nonce)
        jsonObject.put("ratchetKey", ratchetKey)
        jsonObject.put("sequenceNumber", sequenceNumber)
        jsonObject.put("lastSequenceNumber", lastSequenceNumber)
        return jsonObject.toString()
    }

    companion object {
        fun decodeNetworkMessage(string: String): NetworkMessage {
            val jsonObject = JSONObject(string)
            val sender = jsonObject.get("sender") as String
            val payload = jsonObject.get("payload") as String
            val messageType = jsonObject.get("messageType") as String
            val signature = jsonObject.get("signature") as String
            val nonce = jsonObject.get("nonce") as Int
            val ratchetKey = jsonObject.getInt("ratchetKey")
            val sequenceNumber = jsonObject.get("sequenceNumber") as Int
            val lastSequenceNumber = jsonObject.get("lastSequenceNumber") as Int
            return NetworkMessage(sender, payload, messageType, signature, nonce, sequenceNumber, lastSequenceNumber, ratchetKey)
        }
    }
}
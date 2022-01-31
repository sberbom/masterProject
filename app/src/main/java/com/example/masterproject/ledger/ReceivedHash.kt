package com.example.masterproject.ledger

data class ReceivedHash (val hash: String, val signature: String, val senderBlock: LedgerEntry)
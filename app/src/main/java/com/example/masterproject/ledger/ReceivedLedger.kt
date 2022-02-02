package com.example.masterproject.ledger

data class ReceivedLedger (val ledger: List<LedgerEntry>, val hash: String, val senderBlock: LedgerEntry)
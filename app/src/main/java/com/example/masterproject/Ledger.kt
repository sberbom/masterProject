package com.example.masterproject

import android.util.Log

class Ledger {

    companion object {
        private var availableDevices: MutableList<LedgerEntry> = mutableListOf();
        private val TAG = "LedgerEntry"

        fun getLedgerEntry(userName: String): LedgerEntry? {
            for(entry in availableDevices){
                if(entry.userName == userName){
                    return entry
                }
            }
            return null
        }

        fun getFullLedger(): MutableList<LedgerEntry> {
            return availableDevices
        }

        fun addLedgerEntry(ledgerEntry: LedgerEntry) {
            val existingUserNames = availableDevices.map { it.userName }
            val userNameAlreadyExists = existingUserNames.contains(ledgerEntry.userName)
            if (LedgerEntry.ledgerEntryIsValid(ledgerEntry) && !userNameAlreadyExists) {
                Log.d(TAG, "${ledgerEntry.userName} added to ledger")
                availableDevices.add(ledgerEntry)
            } else {
                Log.d(TAG, "${ledgerEntry.userName} not added to ledger")
            }
        }

        fun addFullLedger(ledger: List<LedgerEntry>) {
            if (ledgerIsValid(availableDevices)) {
                Log.d(TAG, "Ledger set to ${ledger.toString()}")
                availableDevices = ledger as MutableList<LedgerEntry>
            }
        }

        fun ledgerIsValid(ledger: List<LedgerEntry>): Boolean {
            val userNames = ledger.map{it.userName}
            var isValid = true
            ledger.forEach{
                val entry = it
                val entryIsInternallyValid = LedgerEntry.ledgerEntryIsValid(entry)
                val onlyOeOccuranceOfUsernameInLedger = userNames.count{it == entry.userName} == 1
                isValid = isValid && entryIsInternallyValid && onlyOeOccuranceOfUsernameInLedger
            }
            return isValid
        }
    }
}
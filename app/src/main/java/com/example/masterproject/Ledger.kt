package com.example.masterproject

class Ledger {
    companion object {
        var availableDevices: MutableList<LedgerEntry> = mutableListOf();

        fun getLedgerEntry(userName: String): LedgerEntry? {
            for(entry in availableDevices){
                if(entry.userName == userName){
                    return entry
                }
            }
            return null
        }
    }
}
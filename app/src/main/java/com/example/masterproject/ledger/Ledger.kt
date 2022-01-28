package com.example.masterproject.ledger

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.masterproject.App
import com.example.masterproject.activities.ChatActivity
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import java.lang.Exception

class Ledger {

    companion object {
        var availableDevices: MutableList<LedgerEntry> = mutableListOf();
        private const val TAG = "LedgerEntry"
        private var myLedgerEntry: LedgerEntry? = null

        fun createNewBlockFromEmail(email: String) {
            if (getLedgerEntry(email) == null) {
                val context = App.getAppContext()
                try {
                    val keyPair = PKIUtils.generateECKeyPair()
                    PKIUtils.storePrivateKey(keyPair.private, context!!)
                    val certificate = PKIUtils.generateSelfSignedX509Certificate(email, keyPair)
                    PKIUtils.storeCertificate(certificate, context)
                    val newBlockHeight = getNextBlockHeight()
                    val newPreviousHash = getNextPreviousBlockHash()
                    val myLedgerEntry = LedgerEntry(PKIUtils.getCertificate()!!, email, MISCUtils.getMyIpAddress()!!, newPreviousHash, newBlockHeight)
                    setMyLedgerEntry(myLedgerEntry)
                    availableDevices.add(myLedgerEntry)
                    Handler(Looper.getMainLooper()).post {
                        MainActivity.updateAvailableDevices()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                throw UsernameTakenError("Username is already in use.")
            }
        }

        fun createNewBlockFromStoredCertificate(): LedgerEntry? {
            val context = App.getAppContext()
            val ipAddress = MISCUtils.getMyIpAddress()
            // TODO: If needed it should be handled when context is null
            if (context != null && ipAddress != null) {
                val storedCertificate = PKIUtils.fetchStoredCertificate(context)
                // If the user has a stored certificate it should be broadcast if it does not conflict with ledger
                if (storedCertificate != null){
                    val previousBlockHash = getNextPreviousBlockHash()
                    val blockHeight = getNextBlockHeight()
                    val myLedgerEntry = LedgerEntry(
                        PKIUtils.getCertificate()!!,
                        PKIUtils.getUsernameFromCertificate(storedCertificate),
                        ipAddress,
                        previousBlockHash,
                        blockHeight)
                    setMyLedgerEntry(myLedgerEntry)
                    return myLedgerEntry
                } else {
                    Log.d(TAG, "No certificate stored.")
                }
            }
            return null
        }

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

        private fun getLastBlock(): LedgerEntry? {
            return availableDevices.maxByOrNull { it.height }
        }

        fun addLedgerEntry(ledgerEntry: LedgerEntry) {
            // TODO: handle if height is not correct
            if (isValidNewBlock(ledgerEntry)) {
                Log.d(TAG, "${ledgerEntry.userName} added to ledger")
                availableDevices.add(ledgerEntry)
                Handler(Looper.getMainLooper()).post {
                    MainActivity.updateAvailableDevices()
                }            } else {
                Log.d(TAG, "${ledgerEntry.userName} not added to ledger")
            }
        }

        fun addFullLedger(ledger: List<LedgerEntry>) {
            if (ledgerIsValid(ledger) && availableDevices.isEmpty()) {
                Log.d(TAG, "Ledger set to ${ledger.toString()}")
                availableDevices.addAll(ledger)
                availableDevices.sortBy { it.height }
                Handler(Looper.getMainLooper()).post {
                    MainActivity.updateAvailableDevices()
                }            }
        }

        fun shouldSendFullLedger(): Boolean {
            val myLedgerEntry = getMyLedgerEntry() ?: return false
            return try {
                val lastCA = availableDevices.last { PKIUtils.isCASignedCertificate(it.certificate) }
                LedgerEntry.isEqual(lastCA, myLedgerEntry)
            } catch (e: NoSuchElementException) {
                val lastBlock = getLastBlock() ?: return false
                LedgerEntry.isEqual(lastBlock, myLedgerEntry)
            }
        }

        fun ledgerIsValid(ledger: List<LedgerEntry>): Boolean {
            val userNames = ledger.map{it.userName}
            var isValid = true
            ledger.forEach{
                val block = it
                val previousBlock = ledger.find { it.height == block.height - 1 }
                val entryIsInternallyValid = LedgerEntry.ledgerEntryIsValid(block)
                val blockHeightIsCorrect = blockHeightIsCorrect(previousBlock, block)
                val previousHashIsCorrect = previousHashIsCorrect(previousBlock, block)
                val onlyOneOccurrenceOfUsernameInLedger = userNames.count{it == block.userName} == 1
                isValid = isValid &&
                        entryIsInternallyValid &&
                        onlyOneOccurrenceOfUsernameInLedger &&
                        blockHeightIsCorrect &&
                        previousHashIsCorrect
            }
            return isValid
        }

        fun setMyLedgerEntry(entry: LedgerEntry) {
            myLedgerEntry = entry
        }

        fun getMyLedgerEntry(): LedgerEntry? {
            return myLedgerEntry
        }

        private fun isValidNewBlock(newBlock: LedgerEntry): Boolean {
            val existingUserNames = availableDevices.map { it.userName }
            val userNameAlreadyExists = existingUserNames.contains(newBlock.userName)
            val lastBlock = getLastBlock()
            val heightIsCorrect = blockHeightIsCorrect(lastBlock, newBlock)
            val hashIsCorrect = previousHashIsCorrect(lastBlock, newBlock)
            return LedgerEntry.ledgerEntryIsValid(newBlock) && !userNameAlreadyExists && heightIsCorrect && hashIsCorrect
        }

        private fun blockHeightIsCorrect(lastBlock: LedgerEntry?, newBlock: LedgerEntry): Boolean {
            return (lastBlock == null && newBlock.height == 0) ||
                    (lastBlock != null && newBlock.height == (lastBlock.height + 1))
        }

        private fun previousHashIsCorrect(lastBlock: LedgerEntry?, newBlock: LedgerEntry): Boolean {
            return (lastBlock == null && newBlock.previousBlockHash == "0") ||
                    (lastBlock != null && newBlock.previousBlockHash == MISCUtils.hashString(lastBlock.toString()))
        }

        private fun getNextBlockHeight(): Int {
            val lastBlock = getLastBlock() ?: return 0
            return lastBlock.height + 1
        }

        private fun getNextPreviousBlockHash(): String {
            val lastBlock = getLastBlock() ?: return "0"
            return MISCUtils.hashString(lastBlock.toString())
        }

        override fun toString(): String {
            return availableDevices.sortedBy { it.height }.map { it.toString() }.toString()
        }
    }
}
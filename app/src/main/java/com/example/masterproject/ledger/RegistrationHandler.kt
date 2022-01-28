package com.example.masterproject.ledger

import android.os.CountDownTimer
import android.util.Log
import com.example.masterproject.network.MulticastClient
import com.example.masterproject.network.MulticastServer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RegistrationHandler {

    private var fullLedger: List<LedgerEntry> = listOf()

    private var hashes: MutableList<String> = mutableListOf()

    private val client: MulticastClient = MulticastClient()

    private var ledgerIsInitialized: Boolean = false

    private val TAG = "RegistrationHandler"

    // The counter is started when the request for the ledger is sent, and
    // cancelled if we receive an answer. The counter will finish only if
    // there is no answer in X seconds, and if so, we conclude that we are
    // the first ones to join a group.
    private val timer = object: CountDownTimer(2000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}

        override fun onFinish() {
            Log.d(TAG, "Timer finished")
            startRegistration()
        }
    }

    fun startWaitForLedgerTimer() {
        Log.d(TAG, "Timer started")
        GlobalScope.launch {
            timer.start()
        }
    }

    private fun stopTimer() {
        Log.d(TAG, "Timer cancelled")
        timer.cancel()
    }

    fun fullLedgerReceived(ledger: List<LedgerEntry>) {
        if (!ledgerIsInitialized) {
            stopTimer()
            fullLedger = ledger.sortedBy { it.height }
            val ledgerIsValid = Ledger.ledgerIsValid(fullLedger)
            Log.d(TAG, "Ledger is valid: $ledgerIsValid")
            // TODO: Handle what to do if ledger is not valid
            if (ledgerIsAccepted() && Ledger.ledgerIsValid(fullLedger)) {
                ledgerIsInitialized = true
                Ledger.addFullLedger(ledger)
                startRegistration()
            }
        }
    }

    fun hashOfLedgerReceived(hash: String) {
        stopTimer()
        hashes.add(hash)
    }

    private fun startRegistration() {
        if (Ledger.getMyLedgerEntry() == null) {
            readyForRegistration = true
            Log.d(TAG, "Registration started")
            val myExistingBlock = Ledger.existingBlockFromStoredCertificate()
            if (myExistingBlock != null) {
                Ledger.setMyLedgerEntry(myExistingBlock)
            } else {
                val myLedgerEntry = Ledger.createNewBlockFromStoredCertificate()
                if (myLedgerEntry != null) {
                    GlobalScope.launch {
                        client.broadcastBlock()
                    }
                }
            }
        }
    }

    // TODO: Implement function. Ledger should be accepted if enough people has confirmed the ledger.
    // Returns whether we can conclude that the ledger is correct
    private fun ledgerIsAccepted(): Boolean {
        return true
    }

    companion object {
        private var readyForRegistration: Boolean = false

        fun getReadyForRegistration(): Boolean {
            return readyForRegistration
        }
    }
}

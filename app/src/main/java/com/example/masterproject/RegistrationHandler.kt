package com.example.masterproject

import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import com.example.masterproject.Ledger.Companion.availableDevices
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RegistrationHandler(server: MulticastServer) {

    private var readyForRegistration: Boolean = false

    private var fullLedger: List<LedgerEntry> = listOf()

    private var hashes: MutableList<String> = mutableListOf()

    private val client: MulticastClient = MulticastClient()

    private val TAG = "RegistrationHandler"

    // The counter is started when the request for the ledger is sent, and
    // cancelled if we receive an answer. The counter will finish only if
    // there is no answer in X seconds, and if so, we conclude that we are
    // the first ones to join a group.
    private val timer = object: CountDownTimer(20000, 1000) {
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

    fun stopTimer() {
        Log.d(TAG, "Timer cancelled")
        timer.cancel()
    }

    fun fullLedgerReceived(ledger: List<LedgerEntry>) {
        Log.d(TAG, "Full ledger received ${ledger.map { it.toString() }.toString()}")
        stopTimer()
        fullLedger = ledger
        if (ledgerIsAccepted()) {
            availableDevices.addAll(ledger)
            startRegistration()
        }
    }

    fun hashOfLedgerReceived(hash: String) {
        stopTimer()
        hashes.add(hash)
        Log.d(TAG, "Hash received $hash")
    }

    private fun startRegistration() {
        Log.d(TAG, "Registration started")
        readyForRegistration = true
        val context = App.getAppContext()
        // TODO: If needed it should be handled when context is null
        if (context != null) {
            val storedCertificate = Utils.fetchStoredCertificate(context)
            // If the user has a stored certificate it should be broadcasted if it does not conflict with ledger
            if (storedCertificate != null){
                val myLedgerEntry = LedgerEntry(Utils.getCertificate()!!, Utils.getUsernameFromCertificate(storedCertificate), Utils.getMyIpAddress()!!)
                Utils.myLedgerEntry = myLedgerEntry
                // TODO: Check if my username is not the same as some other user
                GlobalScope.launch {
                    client.broadcastBlock()
                }
            } else {
                Log.d(TAG, "No certificate stored.")
            }
        }
    }

    // TODO: Implement function. Ledger should be accepted if enough people has confirmed the ledger.
    // Returns whether we can conclude that the ledger is correct
    private fun ledgerIsAccepted(): Boolean {
        return true
    }
}

package com.example.masterproject

import android.os.CountDownTimer
import android.util.Log

class RegistrationHandler(server: MulticastServer) {

    private var noPeersFound: Boolean = false
    private var timerFinishedOrStopped: Boolean = false

    private var readyForRegistration: Boolean = false

    private var fullLedger: List<LedgerEntry> = listOf()

    private var hashes: MutableList<String> = mutableListOf()

    private val TAG = "RegistrationHandler"

    private val timer = object: CountDownTimer(20000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}

        override fun onFinish() {
            noPeersFound = true
            timerFinishedOrStopped = true
            Log.d(TAG, "Timer finished")
        }
    }

    fun startWaitForLedgerTimer() {
        Log.d(TAG, "Timer started")
        timer.start()
    }

    fun stopTimer() {
        Log.d(TAG, "Timer cancelled")
        timer.cancel()
        timerFinishedOrStopped = true
    }

    fun fullLedgerReceived(ledger: List<LedgerEntry>) {
        stopTimer()
        fullLedger = ledger
        Log.d(TAG, "Ledger received ${ledger.map { it.toString() }.toString()}")
    }

    fun hashOfLedgerReceived(hash: String) {
        stopTimer()
        hashes.add(hash)
        Log.d(TAG, "Hash received $hash")
    }
}

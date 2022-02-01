package com.example.masterproject.ledger

import android.os.CountDownTimer
import android.util.Log
import com.example.masterproject.network.MulticastClient
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RegistrationHandler {

    private var fullLedger: List<LedgerEntry> = listOf()

    private var hashes: MutableList<ReceivedHash> = mutableListOf()

    private val client: MulticastClient = MulticastClient()

    private var listenedForMoreThanOneSecond: Boolean = false

    private var ledgerIsInitialized: Boolean = false

    private val TAG = "RegistrationHandler"

    // The counter is started when the request for the ledger is sent, and
    // cancelled if we receive an answer. The counter will finish only if
    // there is no answer in X seconds, and if so, we conclude that we are
    // the first ones to join a group.
    private val waitForResponseTimer = object: CountDownTimer(1000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}

        override fun onFinish() {
            Log.d(TAG, "Timer finished")
            startRegistration()
        }
    }

    private val acceptanceTimer = object: CountDownTimer(1000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}

        override fun onFinish() {
            Log.d(TAG, "Timer finished")
            listenedForMoreThanOneSecond = true
        }
    }

    fun startWaitForLedgerTimer() {
        Log.d(TAG, "Timer started")
        GlobalScope.launch {
            acceptanceTimer.start()
            waitForResponseTimer.start()
        }
    }

    private fun stopTimer() {
        Log.d(TAG, "Timer cancelled")
        waitForResponseTimer.cancel()
    }

    // TODO: Make sure the same user cannot send both hash and ledger
    // TODO: If second ledger received equals the first, it should be stored as hash, if not it should be stored as alternative ledger
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

    // TODO: Check if only CA verified ledgers should be added
    // TODO: Make sure the same user cannot send both hash and ledger
    fun hashOfLedgerReceived(receivedHash: ReceivedHash) {
        stopTimer()
        if (hashes.count { LedgerEntry.isEqual(it.senderBlock, receivedHash.senderBlock) } == 0) {
            hashes.add(receivedHash)
        }
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
    /**
     * @return whether we can conclude that the ledger is correct
     * We do so if there are 2 or more CA certified hashes validating the full ledger
     * and they account for more than half of the CA certified blocks in the ledger
     * (excluding the one that sent the ledger)
     * OR
     * There are no CA certified hashes unlike the hash of the full ledger
     * OR
     * More than one second has passed since the request AND either there are some CA certified
     * in the ledger (if so the hashes should not matter since they are either in favor of
     * the ledger or not CA certified) OR more than half of the hashes validate the ledger
     */
    private fun ledgerIsAccepted(): Boolean {
        acceptanceTimer.cancel()
        val numberOfCACertifiedInLedger = fullLedger.count { PKIUtils.isCASignedCertificate(it.certificate) }
        val hashOfFullLedger = MISCUtils.hashString(fullLedger.sortedBy { it.height }.map { it.toString() }.toString())
        val numberOfCACertifiedValidations = hashes.count { it.hash == hashOfFullLedger && PKIUtils.isCASignedCertificate(it.senderBlock.certificate) }
        // If two or more CA certified users have approved the ledger and they are more then half of the validators, the ledger should be accepted
        if (numberOfCACertifiedValidations > 1 && numberOfCACertifiedValidations > (numberOfCACertifiedInLedger - 1) / 2) return true
        val numberOfCACertifiedDisapprovals = hashes.count { it.hash != hashOfFullLedger && PKIUtils.isCASignedCertificate(it.senderBlock.certificate) }
        // If there is a disapproval and no more than half of validators have approved, the ledger should not yet be accepted
        if (numberOfCACertifiedDisapprovals > 0) return false
        val hashesValidatingLedger = hashes.count { it.hash == hashOfFullLedger }
        // TODO: Should we always accept if enough validations has been received in 1 second if there are no disapprovals?
        return listenedForMoreThanOneSecond && (numberOfCACertifiedInLedger > 0 || hashesValidatingLedger > (fullLedger.size - 1) / 2)
    }

    companion object {
        private var readyForRegistration: Boolean = false

        fun getReadyForRegistration(): Boolean {
            return readyForRegistration
        }
    }
}

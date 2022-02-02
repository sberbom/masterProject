package com.example.masterproject.ledger

import android.os.CountDownTimer
import android.util.Log
import com.example.masterproject.network.MulticastClient
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RegistrationHandler {

    private var fullLedger: List<LedgerEntry> = listOf()

    private var backupLedger: List<LedgerEntry> = listOf()

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
            Log.d(TAG, "Acceptance timer finished")
            listenedForMoreThanOneSecond = true
            setLedgerIfAccepted()
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
            if (!Ledger.ledgerIsValid(ledger)) return
            stopTimer()
            val sortedLedger = ledger.sortedBy { it.height }
            fullLedger = sortedLedger
            setLedgerIfAccepted()
        }
    }

    private fun setLedgerIfAccepted() {
        val ledger = fullLedger
        val hashOfFullLedger = MISCUtils.hashString(Ledger.toString(ledger))
        val hashOfAcceptedLedger = getHashOfAcceptedLedger(hashOfFullLedger)
        if (hashOfAcceptedLedger != null) {
            acceptanceTimer.cancel()
            if (hashOfAcceptedLedger == hashOfFullLedger) {
                ledgerIsInitialized = true
                Ledger.addFullLedger(ledger)
                startRegistration()
            } else {
                val sentCorrectHash = hashes.find { it.hash == hashOfAcceptedLedger }
                GlobalScope.launch(Dispatchers.IO) {
                    client.requestSpecificHash(
                        hashOfAcceptedLedger,
                        sentCorrectHash!!.senderBlock.userName
                    )
                }
            }
        }
    }

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
                    GlobalScope.launch(Dispatchers.IO) {
                        client.broadcastBlock()
                    }
                }
            }
        }
    }

    // TODO: Implement function. Ledger should be accepted if enough people has confirmed the ledger.
    // TODO: Check if the one who sent the ledger is CA certified, if not it should not be accepted based on numberOfCACertifiedInLedger
    // Returns whether we can conclude that the ledger is correct
    /**
     * @return whether we can conclude that the ledger is correct
     * We do so if there are 2 or more CA certified hashes validating the full ledger
     * and they account for more than half of the CA certified blocks in the ledger
     * (excluding the one that sent the ledger)
     * OR
     * More than one second has passed since the request AND there are no CA certified hashes
     * disapproving the ledger AND either there are some CA certified in the ledger
     * (if so the hashes should not matter since they are either in favor of
     * the ledger or not CA certified) OR more than half of the hashes validate the ledger
     */
    private fun getHashOfAcceptedLedger(hashOfFullLedger: String): String? {
        val numberOfCACertifiedInLedger = fullLedger.count { PKIUtils.isCASignedCertificate(it.certificate) }
        val caVerifiedValidators = hashes.filter { PKIUtils.isCASignedCertificate(it.senderBlock.certificate) }
        val mostCommonHashAmongCACertified = getMostCommonHash( caVerifiedValidators, hashOfFullLedger )
        val numberOfCACertifiedValidations = caVerifiedValidators.count { it.hash == hashOfFullLedger }
        // If two or more CA certified users have approved the ledger and they are more then half of the validators, the ledger should be accepted
        if (numberOfCACertifiedValidations >= 2 && numberOfCACertifiedValidations > (numberOfCACertifiedInLedger - 1) / 2) return hashOfFullLedger
        val mostCommonHash = getMostCommonHash(hashes, hashOfFullLedger)
        return when {
            (!listenedForMoreThanOneSecond) -> null
            // TODO: Should return hashOfFullLedger if sender of ledger is CA verified
            (caVerifiedValidators.isNotEmpty()) -> mostCommonHashAmongCACertified
            else -> mostCommonHash
        }

    }

    private fun getMostCommonHash(hashes: List<ReceivedHash>, hashOfFullLedger: String): String {
        val hashToCount = mutableMapOf(hashOfFullLedger to 1)
        hashes.forEach {
            hashToCount[it.hash] = (hashToCount[it.hash] ?: 0) + 1
        }
        val mostCommonHash = hashToCount.maxByOrNull { it.value }
        // Because maxOrByNull returns first max, we have to explicitly return hashOfFullLedger
        // when mostCommonHash and count of hashOfFullLedger is equal
        // hashToCount[hashOfFullLedger] is never null since it is added in the beginning
        return if (mostCommonHash != null && mostCommonHash.value > hashToCount[hashOfFullLedger]!!) {
            mostCommonHash.key
        } else {
            hashOfFullLedger
        }
    }

    companion object {
        private var readyForRegistration: Boolean = false

        fun getReadyForRegistration(): Boolean {
            return readyForRegistration
        }
    }
}

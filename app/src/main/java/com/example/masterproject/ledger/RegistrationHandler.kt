package com.example.masterproject.ledger

import android.os.CountDownTimer
import android.util.Log
import com.example.masterproject.network.MulticastClient
import com.example.masterproject.network.MulticastServer
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.schedule

class RegistrationHandler(private val server: MulticastServer, private val nonce: Int) {

    private var receivedLedgers: MutableList<ReceivedLedger> = mutableListOf()

    private var hashes: MutableList<ReceivedHash> = mutableListOf()

    private val client: MulticastClient = MulticastClient(server)

    private val broadcastBlocks = mutableListOf<LedgerEntry>()

    private var listenedForMoreThanOneSecond: Boolean = false

    private val TAG = "RegistrationHandler:$nonce"

    private val responseTimeout = Timer()

    private var responseTimeoutCancelled = false

    private val acceptLedgerTimeout = Timer()

    private var acceptLedgerTimeoutCancelled = false

    fun startTimers() {
        Log.d(TAG, "Timer started")
        GlobalScope.launch (Dispatchers.Default) {
            responseTimeout.schedule(1000) {
                if (!responseTimeoutCancelled) {
                    Log.d(TAG, "Timer finished")
                    startRegistration()
                }
            }
            acceptLedgerTimeout.schedule(1000) {
                if (!acceptLedgerTimeoutCancelled) {
                    Log.d(TAG, "Acceptance timer finished")
                    listenedForMoreThanOneSecond = true
                    setLedgerIfAccepted()
                }
            }
        }
    }

    private fun stopTimer() {
        Log.d(TAG, "Timer cancelled")
        responseTimeoutCancelled = true
    }

    fun fullLedgerReceived(sender: LedgerEntry, ledger: List<LedgerEntry>) {
        val sortedLedger = ledger.sortedBy { it.userName }
        if (!Ledger.ledgerIsValid(ledger)) throw Exception("Ledger received by ${sender.userName} is not valid.")
        if (userHasAlreadyResponded(sender)) {
            Log.d(TAG, "User has already responded.")
            return
        }
        stopTimer()
        val hashOfReceivedLedger = Ledger.getHashOfLedger(sortedLedger)
        if (receivedLedgers.map { it.hash }.contains(hashOfReceivedLedger)) {
            addHash(sender, hashOfReceivedLedger)
        } else {
            receivedLedgers.add(ReceivedLedger(ledger, hashOfReceivedLedger, sender))
        }
        setLedgerIfAccepted()

    }

    private fun userHasAlreadyResponded(user: LedgerEntry): Boolean {
        val userHasAlreadyRespondedWithHash = hashes.map { it.senderBlock.certificate.toString() }.contains(user.certificate.toString())
        val userHasAlreadyRespondedWithLedger = receivedLedgers.map { it.senderBlock.certificate.toString() }.contains(user.certificate.toString())
        return userHasAlreadyRespondedWithHash || userHasAlreadyRespondedWithLedger
    }

    private fun setLedgerIfAccepted() {
        val hashOfAcceptedLedger = getHashOfAcceptedLedger() ?: return
        acceptLedgerTimeoutCancelled = true
        val acceptedLedger = receivedLedgers.find { it.hash == hashOfAcceptedLedger }
        if (acceptedLedger != null) {
            Log.d(TAG, "Accepted ledger from ${acceptedLedger.senderBlock.userName}: ${acceptedLedger.ledger}")
            Ledger.setFullLedger(acceptedLedger.ledger, broadcastBlocks)
            startRegistration()
            server.registrationProcessFinished(nonce)
        } else {
            // if the most common hash is not null and does not exist in receivedLedgers
            // it must be found in hashes, therefore we can use non-null assertion
            val sentCorrectHash = hashes.find { it.hash == hashOfAcceptedLedger }!!.senderBlock.userName
            Log.d(TAG, "Accepted hash from $sentCorrectHash: $hashOfAcceptedLedger")
            GlobalScope.launch(Dispatchers.IO) {
                client.requestSpecificHash(
                    hashOfAcceptedLedger,
                    sentCorrectHash
                )
            }

        }

    }

    private fun addHash(senderBlock: LedgerEntry, hash: String) {
        val certificateIsValid = PKIUtils.isCASignedCertificate(senderBlock.certificate) || PKIUtils.isSelfSignedCertificate(senderBlock.certificate)
        if (certificateIsValid) {
            hashes.add(ReceivedHash(hash, senderBlock))
        }
    }

    fun hashOfLedgerReceived(senderBlock: LedgerEntry, hash: String) {
        stopTimer()
        if (userHasAlreadyResponded(senderBlock)) {
            Log.d(TAG, "${senderBlock.userName} has already responded.")
        }
        addHash(senderBlock, hash)
    }

    fun addBroadcastBlock(block: LedgerEntry) {
        if (broadcastBlocks.map { it.certificate }.contains(block.certificate)) return
        broadcastBlocks.add(block)
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
                        client.broadcastBlock(nonce)
                    }
                }
            }
        }
    }

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
    private fun getHashOfAcceptedLedger(): String? {
        val caVerifiedValidators = hashes.filter { PKIUtils.isCASignedCertificate(it.senderBlock.certificate) }
        val mostCommonHashAmongCACertified = getMostCommonHash( caVerifiedValidators, true )
        val ledgerWithMostCommonHashAmongCACertified = receivedLedgers.find { it.hash == mostCommonHashAmongCACertified }
        val numberOfCACertifiedInLedger = ledgerWithMostCommonHashAmongCACertified?.ledger?.count { PKIUtils.isCASignedCertificate(it.certificate) }
        val numberOfCACertifiedValidations = caVerifiedValidators.count { it.hash == mostCommonHashAmongCACertified }
        // If two or more CA certified users have approved the ledger and they are more then half of the validators, the ledger should be accepted
        if (numberOfCACertifiedInLedger != null && numberOfCACertifiedValidations >= 2 && numberOfCACertifiedValidations > (numberOfCACertifiedInLedger - 1) / 2) return mostCommonHashAmongCACertified
        val mostCommonHash = getMostCommonHash(hashes, false)
        return when {
            (!listenedForMoreThanOneSecond) -> null
            (ledgerWithMostCommonHashAmongCACertified != null) -> mostCommonHashAmongCACertified
            else -> mostCommonHash
        }

    }

    private fun getMostCommonHash(hashes: List<ReceivedHash>, onlyCACertified: Boolean): String? {
        val hashToCount = mutableMapOf<String, Int>()
        // There should be no entries in receivedLedgers with same hash, so all can be set to 1
        receivedLedgers.forEach {
            if (!onlyCACertified || PKIUtils.isCASignedCertificate(it.senderBlock.certificate))
                hashToCount[it.hash] = 1
        }
        hashes.forEach {
            hashToCount[it.hash] = (hashToCount[it.hash] ?: 0) + 1
        }
        if (hashToCount.isEmpty()) return null
        val maxValue = hashToCount.values.maxOrNull()
        val mostCommonHashes = hashToCount.filterValues { it == maxValue }
        val mostCommonReceivedLedger = receivedLedgers.filter { mostCommonHashes.contains(it.hash) }.maxByOrNull { it.ledger.size }
        // If the ledger of one or more of the most common hashes has been received, take the longest one,
        // if not take the first of the most common hashes.
        return mostCommonReceivedLedger?.hash ?: mostCommonHashes.entries.first().key
    }

    companion object {
        private var readyForRegistration: Boolean = false

        fun getReadyForRegistration(): Boolean {
            return readyForRegistration
        }

    }
}

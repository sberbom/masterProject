package com.example.masterproject.ledger

import android.util.Log
import com.example.masterproject.network.MulticastClient
import com.example.masterproject.network.MulticastServer
import com.example.masterproject.types.MulticastPacket
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.security.cert.X509Certificate
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.random.Random.Default.nextInt

class RegistrationHandler(private val server: MulticastServer, private val nonce: Int, val isMyRegistration: Boolean) {

    private var receivedLedgers: MutableList<ReceivedLedger> = mutableListOf()

    private var hashes: MutableList<ReceivedHash> = mutableListOf()

    private var listenedForMoreThanOneSecond: Boolean = false

    private val TAG = "RegistrationHandler"

    private var acceptedHash: String? = null


    private val acceptLedgerTimeout = Timer()

    private var acceptLedgerTimeoutCancelled = false

    private val firstInNetworkTimer = Timer()

    private val requestLedgerOfAcceptedHashTimer = Timer()

    private var requestLedgerOfAcceptedHashTimerIsActive = false

    private var requestLedgerOfAcceptedHashTimerCancelled = false

    private var requestLedgerOfAcceptedHashCounter = 0

    private val certificateStringToSenderBlock: MutableMap<String, LedgerEntry> = mutableMapOf()

    private val usernameToCertificates: MutableMap<String, MutableList<X509Certificate>> = mutableMapOf()

    private val ledgerFragmentsReceived: MutableMap<String, MutableList<String?>> = mutableMapOf()

    private val orphanFragment: MutableMap<String, MutableList<MulticastPacket>> = mutableMapOf()

    fun startTimers() {
        GlobalScope.launch (Dispatchers.Default) {
            acceptLedgerTimeout.schedule(10000) {
                if (!acceptLedgerTimeoutCancelled) {
                    listenedForMoreThanOneSecond = true
                    setLedgerIfAccepted()
                }
            }
            firstInNetworkTimer.schedule(2000) {
                if (hashes.isEmpty() && receivedLedgers.isEmpty() && ledgerFragmentsReceived.isEmpty()) {
                    Log.d(TAG, "I am the first in the network")
                    startRegistration()
                }
            }
        }
    }

    fun handleSendLedger(receivedLedger: ReceivedLedger) {
        if (!receivedLedgers.map{PKIUtils.certificateToString(it.senderBlock.certificate)}.contains(PKIUtils.certificateToString(receivedLedger.senderBlock.certificate))) {
            if (receivedLedgers.map { it.hash }.contains(receivedLedger.hash)) {
                addHash(receivedLedger.senderBlock, receivedLedger.hash)
            } else {
                receivedLedgers.add(receivedLedger)
            }
        }
    }

    fun fullLedgerReceived(multicastPacket: MulticastPacket) {
        val user = getSenderBlock(null, multicastPacket)
        // If user has already sent full ledger, there is no need to handle packet
        if (user != null && receivedLedgers.map { PKIUtils.certificateToString(it.senderBlock.certificate) }.contains(PKIUtils.certificateToString(user.certificate))) return
        val receivedLedger: ReceivedLedger = handleLedgerFragment(multicastPacket) ?: return
        // If the received ledger is the ledger with the accepted hash, accept it
        if (receivedLedger.hash == acceptedHash) {
            Log.d(TAG, "$nonce Received ledger of accepted hash from ${user?.userName}")
            handleAcceptedLedger(receivedLedger)
        }
        // If the you have previously received a hash from the user that sent the ledger, remove the hash and store the ledger
        // as this is probably a ledger that has been accepted by someone else and will therefore also be accepted by you
        val receivedHashOfSender = hashes.find { PKIUtils.certificateToString(it.senderBlock.certificate) == PKIUtils.certificateToString(receivedLedger.senderBlock.certificate) }
        if (receivedHashOfSender != null) {
            hashes.remove(receivedHashOfSender)
            receivedLedgers.add(receivedLedger)
        }
        Log.d(TAG, "Received full ledger from ${receivedLedger.senderBlock.userName} with nonce ${multicastPacket.nonce}")
        Log.d(TAG, "FULL_LEDGER ${multicastPacket.nonce}")
        if(!Ledger.ledgerIsValid(receivedLedger.ledger)) return
        if (receivedLedger.hash == acceptedHash) {
            requestLedgerOfAcceptedHashTimer.cancel()
            requestLedgerOfAcceptedHashTimerCancelled = true
            requestLedgerOfAcceptedHashTimerIsActive = false
            handleAcceptedLedger(receivedLedger)
            return
        }
        if (receivedLedgers.map { it.hash }.contains(receivedLedger.hash)) {
            addHash(receivedLedger.senderBlock, receivedLedger.hash)
        } else {
            receivedLedgers.add(receivedLedger)
        }
        setLedgerIfAccepted()
    }

    /**
     * @return if ledger is complete, the ledger is returned, if not, the fragment is stored and null is returned
     */
    private fun handleLedgerFragment(multicastPacket: MulticastPacket): ReceivedLedger? {
        // if the ledger is separated into several packets, handle the fragment...
        if (multicastPacket.lastSequenceNumber > 0) {
            return if (multicastPacket.sequenceNumber == 0) {
                handleFirstPacket(multicastPacket)
            } else {
                handleNonFirstPackets(multicastPacket)
            }
            // ... if not, return the full ledger
        } else {
            val senderBlock = LedgerEntry.parseString(multicastPacket.sender)
            // return null if signature is not valid
            if (!PKIUtils.verifySignature(multicastPacket.payload, multicastPacket.signature, senderBlock.certificate.publicKey, multicastPacket.nonce)) return null
            val ledger = formatLedgerFragments(mutableListOf(multicastPacket.payload))
            val hash = Ledger.getHashOfLedger(ledger)
            return ReceivedLedger(ledger, hash, senderBlock)
        }
    }

    private fun handleNonFirstPackets(multicastPacket: MulticastPacket): ReceivedLedger? {
        val sender = getSenderBlock(null, multicastPacket)
        val certificateString = if (sender != null) PKIUtils.certificateToString(sender.certificate) else null
        val ledgerSequenceId = if (certificateString != null) "${multicastPacket.nonce}:${certificateString}" else null
        val ledgerFragments = ledgerFragmentsReceived[ledgerSequenceId]
        // if ledger fragments is null the first block in the sequence has not been received
        // and the signature cannot be validated, so the packet should be stored as an orphan
        // null should be returned...
        if (ledgerFragments == null || sender == null) {
            val existingOrphanOfSameUsername = orphanFragment[multicastPacket.sender]
            if (existingOrphanOfSameUsername != null) {
                existingOrphanOfSameUsername.add(multicastPacket)
            } else {
                orphanFragment[multicastPacket.sender] = mutableListOf(multicastPacket)
            }
            return null
        }
        // ...if the first block of the sequence has been received and
        // we have not received this message before it should be stored
        if (ledgerFragments[multicastPacket.sequenceNumber] == null) ledgerFragments[multicastPacket.sequenceNumber] = multicastPacket.payload
        // if all fragments have not yet been received, we should return
        val remainingFragments = ledgerFragments.count { it == null }
        if (remainingFragments > 0) return null
        // if all fragments have been received we should return the ledger
        val senderBlock = certificateStringToSenderBlock[certificateString]
        val ledger = formatLedgerFragments(ledgerFragments as MutableList<String>)
        val hash = Ledger.getHashOfLedger(ledger)
        return ReceivedLedger(ledger, hash, senderBlock!!)
    }

    private fun formatLedgerFragments(fragments: MutableList<String>): List<LedgerEntry> {
        val ledgerEntries: MutableList<LedgerEntry> = mutableListOf()
        // add every entry from every fragment
        fragments.forEach { fragment -> fragment.split(", ").forEach { entry -> ledgerEntries.add(LedgerEntry.parseString(entry)) } }
        return ledgerEntries
    }


    private fun handleFirstPacket(multicastPacket: MulticastPacket): ReceivedLedger? {
        val blockOfSender = LedgerEntry.parseString(multicastPacket.sender)
        val signatureIsValid = PKIUtils.verifySignature(multicastPacket.payload, multicastPacket.signature, blockOfSender.certificate.publicKey, multicastPacket.nonce)
        if (!signatureIsValid) return null
        val certificateString = PKIUtils.certificateToString(blockOfSender.certificate)
        val certificatesToUsername = usernameToCertificates[blockOfSender.userName]
        if (certificatesToUsername == null) {
            usernameToCertificates[blockOfSender.userName] =
                mutableListOf(blockOfSender.certificate)
        } else if (!certificatesToUsername.map { PKIUtils.certificateToString(it) }.contains(certificateString)) {
            certificatesToUsername.add(blockOfSender.certificate)
        }
        certificateStringToSenderBlock[certificateString] = blockOfSender
        val ledgerSequenceId = "${multicastPacket.nonce}:${certificateString}"
        val ledgerFragments = ledgerFragmentsReceived[ledgerSequenceId]
        if (ledgerFragments == null) {
            ledgerFragmentsReceived[ledgerSequenceId] = MutableList(multicastPacket.lastSequenceNumber + 1) {index -> if (index == 0) multicastPacket.payload else null }
        } else if (ledgerFragments[multicastPacket.sequenceNumber] == null) {
            ledgerFragmentsReceived[ledgerSequenceId]!![multicastPacket.sequenceNumber] = multicastPacket.payload
        }
        return handleOrphanBlocks(blockOfSender, ledgerSequenceId)
    }

    private fun handleOrphanBlocks(blockOfSender: LedgerEntry, ledgerSequenceId: String): ReceivedLedger? {
        val orphanFragmentsFromSameUsername = orphanFragment[blockOfSender.userName]
        if (orphanFragmentsFromSameUsername != null) {
            val ledgerFragments = ledgerFragmentsReceived[ledgerSequenceId] ?: return null
            orphanFragmentsFromSameUsername.forEach {
                // if block was signed by sender of this block...
                if (PKIUtils.verifySignature(it.payload, it.signature, blockOfSender.certificate.publicKey, it.nonce)) {
                    // ... and the fragment is not already added, add it
                    if (ledgerFragments[it.sequenceNumber] == null) ledgerFragments[it.sequenceNumber] = it.payload
                }
            }
            if (ledgerFragments.count{it == null} == 0) {
                val ledger = formatLedgerFragments(ledgerFragments as MutableList<String>)
                val hash = Ledger.getHashOfLedger(ledger)
                return ReceivedLedger(ledger, hash, blockOfSender)
            }
        }
        return null
    }

    private fun getSenderBlock(sender: LedgerEntry?, multicastPacket: MulticastPacket?): LedgerEntry? {
        if (sender != null) return sender
        if (multicastPacket == null) return null
        if (multicastPacket.lastSequenceNumber == 0) return LedgerEntry.parseString(multicastPacket.sender)
        val possibleCertificates = usernameToCertificates[multicastPacket.sender]
        val correctCertificate = possibleCertificates?.find { PKIUtils.verifySignature(multicastPacket.payload, multicastPacket.signature, it.publicKey, multicastPacket.nonce) } ?: return null
        return certificateStringToSenderBlock[PKIUtils.certificateToString(correctCertificate)]
    }

    private fun userHasAlreadyResponded(multicastPacket: MulticastPacket?, sender: LedgerEntry?, hashes: List<ReceivedHash>, receivedLedgers: List<ReceivedLedger>): Boolean {
        val user = getSenderBlock(sender, multicastPacket) ?: return false
        val userHasAlreadyRespondedWithHash = hashes.map { PKIUtils.certificateToString(it.senderBlock.certificate) }.contains(PKIUtils.certificateToString(user.certificate))
        val userHasAlreadyRespondedWithLedger = receivedLedgers.map { PKIUtils.certificateToString(it.senderBlock.certificate) }.contains(PKIUtils.certificateToString(user.certificate))
        return userHasAlreadyRespondedWithHash || userHasAlreadyRespondedWithLedger
    }

    private fun setLedgerIfAccepted() {
        if (listenedForMoreThanOneSecond && hashes.isEmpty() && receivedLedgers.isEmpty()) {
            startRegistration()
            return
        }
        val hashOfAcceptedLedger = getHashOfAcceptedLedger(hashes.toList(), receivedLedgers.toList()) ?: return
        acceptLedgerTimeoutCancelled = true
        val acceptedLedger = receivedLedgers.find { it.hash == hashOfAcceptedLedger }
        if (acceptedLedger != null) {
            handleAcceptedLedger(acceptedLedger)
        } else {
            acceptedHash = hashOfAcceptedLedger
            // If the accepted hash is the same is the hash of my ledger, I have the correct ledger and do not need to do anything
            if (acceptedHash == Ledger.getHashOfStoredLedger()) return
            // 0 if my registration, if not random time divided in 20 steps from 200 ms until 1000 seconds
            val waitToRequest = if (isMyRegistration) 0 else 200 + nextInt(20) * 40
            if(!requestLedgerOfAcceptedHashTimerIsActive) {
                requestLedgerOfAcceptedHashTimer.scheduleAtFixedRate(waitToRequest.toLong(), 500) {
                    requestLedgerOfAcceptedHashTimerIsActive = true
                    if (!requestLedgerOfAcceptedHashTimerCancelled && requestLedgerOfAcceptedHashCounter < 3) {
                        requestLedgerOfAcceptedHashCounter += 1
                        requestLedgerOfCorrectHash(hashOfAcceptedLedger)
                    } else {
                        requestLedgerOfAcceptedHashTimer.cancel()
                        requestLedgerOfAcceptedHashTimerIsActive = false
                        startRegistration()
                    }
                }
            }
        }

    }

    private fun handleAcceptedLedger(acceptedLedger: ReceivedLedger) {
        Log.d(TAG, "ACCEPTED_LEDGER $nonce ${acceptedLedger.ledger.map { it.userName }.toSet().size}")
        acceptedLedger.ledger.forEach { Ledger.addLedgerEntry(it) }
        Log.d(TAG, "$nonce The accepted ledger contains: ${acceptedLedger.ledger.map { it.userName }}")
        startRegistration()
    }

    private fun requestLedgerOfCorrectHash(hashOfAcceptedLedger: String) {
        // if the most common hash is not null and does not exist in receivedLedgers
        // it must be found in hashes, therefore we can use non-null assertion
        val sentCorrectHash = hashes.filter { it.hash == hashOfAcceptedLedger }.random().senderBlock.userName
        Log.d(TAG, "$nonce Accepted hash from $sentCorrectHash: $hashOfAcceptedLedger")
        GlobalScope.launch(Dispatchers.IO) {
            MulticastClient.requestSpecificHash(
                hashOfAcceptedLedger,
                sentCorrectHash,
                nonce
            )
        }
    }

    private fun addHash(senderBlock: LedgerEntry, hash: String) {
        val certificateIsValid = PKIUtils.isCASignedCertificate(senderBlock.certificate) || PKIUtils.isSelfSignedCertificate(senderBlock.certificate)
        if (certificateIsValid) {
            hashes.add(ReceivedHash(hash, senderBlock))
        }
    }

    fun hashOfLedgerReceived(senderBlock: LedgerEntry, hash: String) {
        if (userHasAlreadyResponded(null, senderBlock, hashes.toList(), receivedLedgers.toList())) return
        Log.d(TAG, "$nonce Received hash from ${senderBlock.userName}: $hash")
        addHash(senderBlock, hash)
    }

    private fun startRegistration() {
        if (Ledger.myLedgerEntry == null) {
            readyForRegistration = true
            Log.d(TAG, "$nonce Registration started")
            val myExistingBlock = Ledger.existingBlockFromStoredCertificate()
            if (myExistingBlock != null) {
                val currentIp = MISCUtils.getMyIpAddress()
                if (currentIp != null && myExistingBlock.getIpAddress() != currentIp) {
                    myExistingBlock.setIpAddress(currentIp)
                    GlobalScope.launch(Dispatchers.IO) {
                        MulticastClient.sendIpChanged(currentIp)
                    }
                }
                Ledger.myLedgerEntry = myExistingBlock
            } else {
                val myLedgerEntry = Ledger.createNewBlockFromStoredCertificate()
                if (myLedgerEntry != null) {
                    GlobalScope.launch(Dispatchers.IO) {
                        MulticastClient.broadcastBlock(nonce)
                    }
                }
            }
        }
        server.registrationProcessFinished(nonce)
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
    private fun getHashOfAcceptedLedger(hashes: List<ReceivedHash>, receivedLedgers: List<ReceivedLedger>): String? {
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

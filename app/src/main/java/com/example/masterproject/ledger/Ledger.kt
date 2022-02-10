package com.example.masterproject.ledger

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.masterproject.App
import com.example.masterproject.activities.MainActivity
import com.example.masterproject.exceptions.UsernameTakenException
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import java.lang.Exception

class Ledger {

    companion object {
        var availableDevices: MutableList<LedgerEntry> = mutableListOf()
        private const val TAG = "Ledger"
        private var myLedgerEntry: LedgerEntry? = null

        fun createNewBlockFromEmail(email: String) {
            if (getLedgerEntry(email) == null) {
                try {
                    val keyPair = PKIUtils.generateECKeyPair()
                    val certificate = PKIUtils.generateSelfSignedX509Certificate(email, keyPair)
                    PKIUtils.addKeyToKeyStore(keyPair.private, certificate)
                    PKIUtils.writeKeyStoreToFile()
                    val myLedgerEntry = LedgerEntry(PKIUtils.getStoredCertificate()!!, email, MISCUtils.getMyIpAddress()!!)
                    setMyLedgerEntry(myLedgerEntry)
                    availableDevices.add(myLedgerEntry)
                    Handler(Looper.getMainLooper()).post {
                        MainActivity.updateAvailableDevices()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                throw UsernameTakenException("Username is already in use.")
            }
        }

        fun createNewBlockFromStoredCertificate(): LedgerEntry? {
            val ipAddress = MISCUtils.getMyIpAddress()
            if (ipAddress != null) {
                val storedCertificate = PKIUtils.getStoredCertificate()
                // If the user has a stored certificate it should be broadcast if it does not conflict with ledger
                if (storedCertificate != null){
                    val myLedgerEntry = LedgerEntry(
                        storedCertificate,
                        PKIUtils.getUsernameFromCertificate(storedCertificate),
                        ipAddress)
                    if (isValidNewBlock(myLedgerEntry)){
                        Log.d(TAG, "Created block from stored certificate.")
                        setMyLedgerEntry(myLedgerEntry)
                        availableDevices.add(myLedgerEntry)
                        availableDevices.sortBy { it.userName }
                        Handler(Looper.getMainLooper()).post {
                            MainActivity.updateAvailableDevices()
                        }
                        return myLedgerEntry
                    }
                } else {
                    Log.d(TAG, "No certificate stored.")
                }
            }
            return null
        }

        /**
         * @description: Return this users entry in the existing ledger if the user
         *                has a stored certificate that is part of the ledger.
         *                Returns null if not.
         * */
        fun existingBlockFromStoredCertificate(): LedgerEntry? {
            val context = App.getAppContext()
            if (context != null) {
                val storedCertificate = PKIUtils.getStoredCertificate()
                if (storedCertificate != null) {
                    return availableDevices.find { PKIUtils.certificateToString(it.certificate) == PKIUtils.certificateToString(storedCertificate)}
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

        fun addLedgerEntry(newBlock: LedgerEntry) {
            val myLedgerEntry = myLedgerEntry
            val userAlreadyInLedger =
                availableDevices.map { it.certificate }.contains(newBlock.certificate)
            if (isValidNewBlock(newBlock) && !userAlreadyInLedger) {
                val existingBlockWithSameUsername = availableDevices.find { it.userName == newBlock.userName }
                if (existingBlockWithSameUsername != null && !LedgerEntry.isEqual(existingBlockWithSameUsername, newBlock)) {
                    availableDevices.remove(existingBlockWithSameUsername)
                    // TODO: All users should be warned that the user has been updated
                    if (existingBlockWithSameUsername == myLedgerEntry) handleLosingUsername()
                }
                Log.d(TAG, "${newBlock.userName} added to ledger")
                availableDevices.add(newBlock)
                PKIUtils.addCertificateToTrustStore(newBlock.userName, newBlock.certificate)
                availableDevices.sortBy { it.userName }
                Handler(Looper.getMainLooper()).post {
                    MainActivity.updateAvailableDevices()
                }
            } else {
                Log.d(TAG, "${newBlock.userName} not added to ledger")
            }
        }

        fun getHashOfLedger(ledger: List<LedgerEntry>): String {
            return MISCUtils.hashString(toString(ledger.sortedBy { it.userName }))
        }

        fun getHashOfStoredLedger(): String {
            return getHashOfLedger(availableDevices)
        }

        fun shouldSendFullLedger(): Boolean {
            val myLedgerEntry = getMyLedgerEntry() ?: return false
            val myCertificateString = PKIUtils.certificateToString(myLedgerEntry.certificate)
            val caSignedCertificates = availableDevices.filter { PKIUtils.isCASignedCertificate(it.certificate) }.map { PKIUtils.certificateToString(it.certificate) }
            return when {
                caSignedCertificates.isNotEmpty() -> {
                    caSignedCertificates.takeLast(2).contains(myCertificateString)
                }
                else -> {
                    availableDevices.takeLast(2).map { PKIUtils.certificateToString(it.certificate)}
                        .contains(myCertificateString)
                }
            }
        }

        // TODO: Change so that only your own block is not shown
        fun shouldBeRendered(block: LedgerEntry): Boolean {
            if(PKIUtils.isCASignedCertificate(block.certificate)) return true
            availableDevices.forEach{
                if ( it.userName == block.userName && PKIUtils.isCASignedCertificate(it.certificate)) return false
            }
            return true
        }

        fun ledgerIsValid(ledger: List<LedgerEntry>): Boolean {
            ledger.forEach{ block ->
                val entryIsInternallyValid = LedgerEntry.ledgerEntryIsValid(block)
                val usernameInLedgerIsValid = usernameInLedgerIsValid(ledger, block.userName)
                if ((!entryIsInternallyValid ||
                    !usernameInLedgerIsValid)) return false
            }
            return true
        }

        /**
         * @return true if there is only one occurrence of the username or
         * two occurrences where the first is certificate is self-signed
         * and the second is CA signed
         */
        private fun usernameInLedgerIsValid(ledger: List<LedgerEntry>, username: String): Boolean {
            val occurrencesOfUsernameInLedger = ledger.filter { it.userName == username }
            return occurrencesOfUsernameInLedger.size == 1
        }

        fun setMyLedgerEntry(entry: LedgerEntry) {
            myLedgerEntry = entry
        }

        fun getMyLedgerEntry(): LedgerEntry? {
            return myLedgerEntry
        }

        private fun handleLosingUsername() {
            myLedgerEntry = null
            Log.d(TAG, "Your ledger entry has been overwritten due to username conflict.")
            val context = App.getAppContext()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Someone with valid certificate has claimed your username. Please register again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun isValidNewBlock(newBlock: LedgerEntry): Boolean {
            val canUseUsername = canUseUsername(newBlock)
            return LedgerEntry.ledgerEntryIsValid(newBlock) && canUseUsername
        }

        private fun canUseUsername(newBlock: LedgerEntry): Boolean {
            val isCASigned = PKIUtils.isCASignedCertificate(newBlock.certificate)
            val certificateAndUsernameCorresponds = PKIUtils.getUsernameFromCertificate(newBlock.certificate) == newBlock.userName
            val existingUserNames = availableDevices.map { it.userName }
            val userNameIsUnique = !existingUserNames.contains(newBlock.userName)
            return (isCASigned && certificateAndUsernameCorresponds) || userNameIsUnique
        }

        fun toString(ledger: List<LedgerEntry>): String {
            return ledger.sortedBy { it.userName }.map { it.toString() }.toString()
        }

        override fun toString(): String {
            return toString(availableDevices)
        }
    }
}
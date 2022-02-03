package com.example.masterproject

import android.util.Log
import com.example.masterproject.ledger.LedgerEntry
import com.example.masterproject.ledger.RegistrationHandler
import com.example.masterproject.types.NetworkMessage
import com.example.masterproject.utils.PKIUtils
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RegistrationHandlerTest {

    @Before
    fun initialize() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @Test
    fun hashOfLedgerReceivedSuccess() {
        val registrationHandler = RegistrationHandler()
        val senderBlock = LedgerEntry(PKIUtils.stringToCertificate("MIIBGTCBwQIGAX66mRDmMAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDGhkaHpAa2dsLmNvbTAeFw0yMjAyMDExMzIxNDBaFw0yNDAyMDIxMzIxNDBaMBcxFTATBgNVBAMMDGhkaHpAa2dsLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABD2fOzr0XpPJiMGVqL+FStjY5YrMh76ZZOFU/wSbR6e4e8KrnOh4vr2VYHjoulu8IbUO88K6MNtxjCExPO3XtrAwCgYIKoZIzj0EAwIDRwAwRAIgRJfb29H7drp35oqtV97m9frJ4VvPvfafN9UiAbTzMNcCIHEVrWCtLOLdoEblxRnr/tz6gA8FPRuUVaDFZKjpVUj9"),
            "hdhz@kgl.com", "192.168.1.57")
        val hash = "93b285f4d65d657dd443443c381a2d42d8eabef21485aa2644a3301316eed9fc"
        registrationHandler.hashOfLedgerReceived(senderBlock, hash)
        assertEquals(1, registrationHandler.getHashCount())
    }
}
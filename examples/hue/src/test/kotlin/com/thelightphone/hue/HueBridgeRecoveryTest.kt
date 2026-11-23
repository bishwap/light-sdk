package com.thelightphone.hue

import java.net.ConnectException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HueBridgeRecoveryTest {
    @Test
    fun connectionFailureRetriesOnceAtTheSameBridgesNewAddress() = runBlocking {
        val attempted = mutableListOf<String>()
        val failure = ConnectException()
        val (ip, result) = requestWithBridgeRediscovery<Unit>(
            "192.168.1.2", "001788FFFE123ABC",
            discover = {
                Result.success(
                    listOf(
                        DiscoveredBridge("001788FFFE123ABC", "192.168.1.2"),
                        DiscoveredBridge("001788FFFE123ABD", "192.168.1.3"),
                        DiscoveredBridge("001788fffe123abc", "192.168.1.4"),
                    ),
                )
            },
        ) {
            attempted += it
            Result.failure(failure)
        }
        assertEquals(listOf("192.168.1.2", "192.168.1.4"), attempted)
        assertEquals("192.168.1.4", ip)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun hueApiErrorsDoNotTriggerDiscovery() = runBlocking {
        val failure = UnauthorizedException()
        val (ip, result) = requestWithBridgeRediscovery<Unit>(
            "192.168.1.2", "001788FFFE123ABC",
            discover = { error("Discovery should not run") },
            request = { Result.failure(failure) },
        )
        assertEquals("192.168.1.2", ip)
        assertSame(failure, result.exceptionOrNull())
    }
}

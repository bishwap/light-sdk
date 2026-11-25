package com.thelightphone.hue

import javax.security.auth.x500.X500Principal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HueBridgeTlsTest {
    private val bridgeId = "001788FFFE123ABC"

    @Test
    fun leafCommonNameMustMatchThePairedBridgeId() {
        val subject = X500Principal("CN=001788fffe123abc,O=Signify")
        assertTrue(matchesBridgeIdentity(subject, bridgeId))
        assertFalse(matchesBridgeIdentity(subject, "001788FFFE123ABD"))
        assertFalse(matchesBridgeIdentity(subject, ""))
    }

    @Test
    fun missingMalformedAndAmbiguousCommonNamesAreRejected() {
        assertFalse(matchesBridgeIdentity(X500Principal("O=$bridgeId"), bridgeId))
        assertFalse(matchesBridgeIdentity(X500Principal("CN=bridge.example"), null))
        assertFalse(matchesBridgeIdentity(X500Principal("CN=$bridgeId+CN=001788FFFE123ABD"), bridgeId))
    }
}

package com.pocket.opencode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBridgeTest {
    private val token = "test-token_123"

    @Test
    fun bearerHeaderIsAccepted() {
        assertTrue(DebugBridge.authorized("Bearer $token", token))
        assertTrue(DebugBridge.authorized("bearer $token", token))
    }

    @Test
    fun rawHeaderIsAcceptedForLocalTools() {
        assertTrue(DebugBridge.authorized(token, token))
    }

    @Test
    fun missingOrWrongTokenIsRejected() {
        assertFalse(DebugBridge.authorized(null, token))
        assertFalse(DebugBridge.authorized("", token))
        assertFalse(DebugBridge.authorized("Bearer other", token))
        assertFalse(DebugBridge.authorized(token, ""))
    }

    @Test
    fun onlyKnownActionsAreAllowed() {
        assertTrue(DebugBridge.actionAllowed("restart"))
        assertTrue(DebugBridge.actionAllowed("api"))
        assertFalse(DebugBridge.actionAllowed("shell"))
        assertFalse(DebugBridge.actionAllowed("eval"))
    }
}

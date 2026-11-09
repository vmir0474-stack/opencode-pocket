package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {
    @Test
    fun ringKeepsOnlyLatestEvents() {
        val ring = DebugRing(3)
        (1..5).forEach { ring.add(DebugEvent(it.toLong(), "k", "m$it")) }

        assertEquals(3, ring.size())
        assertEquals(listOf("m3", "m4", "m5"), ring.recent(10).map { it.message })
        assertEquals(listOf("m4", "m5"), ring.recent(2).map { it.message })
    }

    @Test
    fun eventJsonHasKindAndTime() {
        val json = DebugEvent(1_700_000_000_000, "api", "GET /api/session failed").toJson()

        assertEquals("api", json.getString("kind"))
        assertTrue(json.has("time"))
        assertTrue(json.getString("message").contains("/api/session"))
    }

    @Test
    fun redactionRemovesSecrets() {
        val text = "password=abc123 Bearer sk-live-1234567890 {\"api_key\":\"key-999\"}"
        val clean = Redaction.redact(text)

        assertFalse(clean.contains("abc123"))
        assertFalse(clean.contains("1234567890"))
        assertFalse(clean.contains("key-999"))
        assertTrue(clean.contains("***"))
    }

    @Test
    fun redactionHandlesServerLogPasswordLine() {
        val clean = Redaction.redact("server password t1LF5Y8eWiyQvdjS8paP6kUCk9MMDqq3")

        assertFalse(clean.contains("t1LF5Y8eWiyQvdjS8paP6kUCk9MMDqq3"))
        assertTrue(clean.startsWith("server password ***"))
    }

    @Test
    fun redactionMasksAuthorizationHeader() {
        val clean = Redaction.redact("Authorization: Bearer abc.def-123_456")

        assertEquals("Authorization: ***", clean)
    }
}

package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialPayloadTest {
    @Test
    fun `key credential matches server schema`() {
        val o = CredentialPayload.key("anthropic", "sk-test")
        assertEquals("anthropic", o.getString("integrationID"))
        assertEquals("default", o.getString("label"))
        assertEquals("key", o.getJSONObject("value").getString("type"))
        assertEquals("sk-test", o.getJSONObject("value").getString("key"))
    }
}

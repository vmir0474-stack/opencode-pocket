package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerOutputTest {
    @Test
    fun `parses plain password line`() {
        assertEquals("abc123", ServerOutput.password("server password abc123"))
    }

    @Test
    fun `parses password with surrounding output`() {
        assertEquals("abc123", ServerOutput.password("OUT server password abc123"))
    }

    @Test
    fun `ignores non password lines`() {
        assertNull(ServerOutput.password("server listening on http://127.0.0.1:4096"))
    }

    @Test
    fun `parses listening url`() {
        assertEquals(
            "http://127.0.0.1:4096",
            ServerOutput.listeningUrl("server listening on http://127.0.0.1:4096")
        )
    }
}

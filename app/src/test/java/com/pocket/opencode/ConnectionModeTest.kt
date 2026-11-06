package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionModeTest {
    @Test
    fun `fresh install defaults to embedded`() {
        assertEquals(ServerMode.EMBEDDED, ConnectionModeResolver.resolve(false, null, false))
    }

    @Test
    fun `existing external server survives upgrade`() {
        assertEquals(ServerMode.EXTERNAL, ConnectionModeResolver.resolve(false, null, true))
    }

    @Test
    fun `saved mode wins`() {
        assertEquals(ServerMode.EMBEDDED, ConnectionModeResolver.resolve(true, "embedded", true))
        assertEquals(ServerMode.EXTERNAL, ConnectionModeResolver.resolve(true, "external", false))
    }
}

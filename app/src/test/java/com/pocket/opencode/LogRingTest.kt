package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRingTest {
    @Test
    fun `keeps insertion order`() {
        val ring = LogRing(3)
        ring.append("a")
        ring.append("b")
        ring.append("c")
        assertEquals(listOf("a", "b", "c"), ring.snapshot())
    }

    @Test
    fun `drops oldest lines past capacity`() {
        val ring = LogRing(3)
        listOf("a", "b", "c", "d").forEach(ring::append)
        assertEquals(listOf("b", "c", "d"), ring.snapshot())
    }

    @Test
    fun `clear empties the ring`() {
        val ring = LogRing(2)
        ring.append("a")
        ring.clear()
        assertTrue(ring.snapshot().isEmpty())
    }

    @Test
    fun `masks the server password`() {
        val ring = LogRing(4)
        ring.append("server listening on http://127.0.0.1:4096")
        ring.append("server password w6BndQiY43UOOR18f9u2xa2S6V7Wlxuwmjn-IF_DMvw")

        val lines = ring.snapshot()
        assertEquals("server listening on http://127.0.0.1:4096", lines[0])
        assertEquals("server password ***", lines[1])
    }

    @Test
    fun `masks bearer tokens`() {
        val ring = LogRing(2)
        ring.append("Authorization: Bearer abc.def-123_456")
        val line = ring.snapshot().single()
        assertTrue(line.contains("***"))
        assertTrue(!line.contains("abc.def"))
    }
}

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
}

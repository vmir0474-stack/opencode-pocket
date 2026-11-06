package com.pocket.opencode

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class PortSelectionTest {
    @Test
    fun `returns preferred port when free`() {
        val free = ServerSocket(0).use { it.localPort }
        assertTrue(PortSelection.choose(free) == free)
    }

    @Test
    fun `skips a busy port`() {
        ServerSocket(0).use { busy ->
            val picked = PortSelection.choose(busy.localPort, span = 8)
            assertNotEquals(busy.localPort, picked)
            assertTrue(picked > 0)
        }
    }
}

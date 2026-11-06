package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Test

class TabMigrationTest {
    @Test
    fun `old settings tab becomes new settings tab`() {
        assertEquals(4, TabMigration.map(2, 1))
    }

    @Test
    fun `current schema is untouched`() {
        assertEquals(2, TabMigration.map(2, 2))
    }

    @Test
    fun `other old tabs keep their index`() {
        assertEquals(0, TabMigration.map(0, 1))
        assertEquals(1, TabMigration.map(1, 1))
    }
}

package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevArchiveTest {

    @Test
    fun normalizesRelativePaths() {
        assertEquals("usr/bin/env", DevArchive.safeRelative("./usr/bin/env"))
        assertEquals("usr/bin", DevArchive.safeRelative("usr/bin/"))
        assertEquals("etc/os-release", DevArchive.safeRelative("./etc//os-release"))
        assertEquals("a/b", DevArchive.safeRelative("a/./b"))
    }

    @Test
    fun rejectsEscapesAndEmptyNames() {
        assertNull(DevArchive.safeRelative("/etc/passwd"))
        assertNull(DevArchive.safeRelative("../etc/passwd"))
        assertNull(DevArchive.safeRelative("a/../../b"))
        assertNull(DevArchive.safeRelative("."))
        assertNull(DevArchive.safeRelative(""))
        assertNull(DevArchive.safeRelative("./"))
    }

    @Test
    fun stripsTypeBitsFromMode() {
        assertEquals(0b111101101, DevArchive.permissions(0b100000111101101))
        assertEquals(420, DevArchive.permissions(0x81A4)) // 0100644 -> 0644
        assertEquals(493, DevArchive.permissions(0x41ED)) // 040755 -> 0755
    }
}

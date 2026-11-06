package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ArchiveLayoutTest {
    private val paths = ServerPaths.under(File("/app/files/opencode"), File("/sdcard/OpenCode"))

    @Test
    fun `maps data directory`() = assertEquals(
        File("/app/files/opencode/home/data/opencode/opencode.db"),
        ArchiveLayout.destinationFor(".local/share/opencode/opencode.db", paths)
    )

    @Test
    fun `maps config directory`() = assertEquals(
        File("/app/files/opencode/home/config/opencode/skills/a/SKILL.md"),
        ArchiveLayout.destinationFor(".config/opencode/skills/a/SKILL.md", paths)
    )

    @Test
    fun `rejects unrelated entry`() {
        assertNull(ArchiveLayout.destinationFor("notes.txt", paths))
    }
}

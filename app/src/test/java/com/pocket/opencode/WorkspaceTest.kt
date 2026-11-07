package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceTest {
    @Test
    fun `default workspace is user visible`() {
        assertEquals("/sdcard/OpenCode", Workspace.defaultPath())
    }

    @Test
    fun `ensure creates nested directory`() {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "oc-test-" + System.nanoTime() + "/nested"
        )
        assertTrue(Workspace.ensure(dir))
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `isUsable checks a real write`() {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "oc-usable-" + System.nanoTime()
        )
        assertTrue(Workspace.isUsable(dir))
        assertFalse(File(dir, ".pocket-write-probe").exists())
    }

    @Test
    fun `isUsable rejects a regular file`() {
        val file = File(
            System.getProperty("java.io.tmpdir"),
            "oc-file-" + System.nanoTime()
        )
        file.writeText("x")
        assertFalse(Workspace.isUsable(file))
        file.delete()
    }
}

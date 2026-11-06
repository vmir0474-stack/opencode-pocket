package com.pocket.opencode

import org.junit.Assert.assertEquals
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
}

package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DevEnvironmentTest {

    private val bin = File("/data/user/0/com.pocket.opencode/files/dev/bin")
    private val root = File("/data/user/0/com.pocket.opencode/files/dev/rootfs")
    private val dev = File("/data/user/0/com.pocket.opencode/files/dev")
    private val workspace = File("/data/user/0/com.pocket.opencode/files/workspace")

    private fun script() = DevEnvironment.wrapperScript(bin, root, workspace, dev)

    @Test
    fun `wrapper points proot at bundled loaders`() {
        val text = script()
        assertTrue(text.contains("export PROOT_LOADER=\"\$BIN/loader\""))
        assertTrue(text.contains("export PROOT_LOADER_32=\"\$BIN/loader32\""))
        assertTrue(text.contains("export LD_LIBRARY_PATH=\"\$BIN\""))
    }

    @Test
    fun `wrapper binds workspace and only readable shared dirs`() {
        val text = script()
        assertTrue(text.contains("-b \"\$WORKSPACE:/workspace\""))
        assertTrue(text.contains("for dir in /sdcard /storage /mnt; do"))
        assertTrue(text.contains("if [ -d \"\$dir\" ] && [ -r \"\$dir\" ]; then"))
        assertFalse(text.contains("-b /sdcard -b /storage -b /mnt"))
    }

    @Test
    fun `wrapper runs proot with fake root and default bash`() {
        val text = script()
        assertTrue(text.contains("--link2symlink --kill-on-exit -0 -r \"\$ROOTFS\""))
        assertTrue(text.contains("set -- /bin/bash -l"))
        assertTrue(text.contains("/usr/bin/env -i HOME=/root"))
        assertTrue(text.endsWith("\"\$@\"\n"))
    }

    @Test
    fun `paths with apostrophes stay quoted`() {
        val odd = File("/data/user/0/o'pocket/files/dev/bin")
        val text = DevEnvironment.wrapperScript(odd, root, workspace, dev)
        assertEquals(true, text.contains("BIN='/data/user/0/o'\\''pocket/files/dev/bin'"))
    }

    @Test
    fun `workspace falls back when external dir is unavailable`() {
        val fallback = File("/data/user/0/com.pocket.opencode/files/workspace")
        val external = File("/sdcard/OpenCode")
        assertEquals(fallback, DevEnvironment.guestWorkspace(external, fallback))
    }

    @Test
    fun `workspace keeps readable directory`() {
        val dir = java.nio.file.Files.createTempDirectory("pocket-ws").toFile()
        try {
            val fallback = File("/data/user/0/com.pocket.opencode/files/workspace")
            assertEquals(dir, DevEnvironment.guestWorkspace(dir, fallback))
        } finally {
            dir.delete()
        }
    }
}

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
    fun `proot version skips the ascii banner`() {
        val banner = " _____ _____              ___\n" +
            "|  __ \\  __ \\_____  _____|   |_\n" +
            "|   __/     /  _  \\/  _  \\    _|\n" +
            "|__|  |__|__\\_____/\\_____/\\____| 5.1.0\n" +
            "\nbuilt-in accelerators: process_vm = yes\n"
        assertEquals("5.1.0", DevEnvironment.prootVersion(banner))
        assertEquals("", DevEnvironment.prootVersion(""))
    }

    @Test
    fun `check script probes python symlink imports and git`() {
        val text = DevEnvironment.checkScript()
        assertTrue(text.contains("probe readlink readlink /usr/bin/python3"))
        assertTrue(text.contains("probe python python3 -V"))
        assertTrue(text.contains("import json,ssl,sqlite3,urllib.request,zlib,bz2,lzma"))
        assertTrue(text.contains("probe git git --version"))
        assertTrue(text.contains("*\"not found\"*"))
    }

    @Test
    fun `failed probe marks environment as broken`() {
        val report = DevEnvironment.parseCheck(
            "shell=ok pocket-dev\n" +
                "readlink=ok python3.12\n" +
                "python=fail(1) python3: Permission denied\n" +
                "git=absent git: not found\n",
        )
        assertEquals(4, report.probes.size)
        assertTrue(report.broken)
        assertEquals("fail(1)", report.probes[2].status)
        assertEquals("python3: Permission denied", report.probes[2].detail)
        assertEquals("absent", report.probes[3].status)
    }

    @Test
    fun `missing packages alone are not a failure`() {
        val report = DevEnvironment.parseCheck(
            "shell=ok pocket-dev\n" +
                "python=absent python3: not found\n" +
                "git=absent git: not found\n" +
                "proot version 5.1\n",
        )
        assertEquals(3, report.probes.size)
        assertFalse(report.broken)
        assertEquals("ok", report.probes[0].status)
        assertEquals("pocket-dev", report.probes[0].detail)
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

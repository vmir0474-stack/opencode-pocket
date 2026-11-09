package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceImporterTest {
    private fun tempDir(): File = File(
        System.getProperty("java.io.tmpdir"),
        "oc-import-" + System.nanoTime()
    ).apply { mkdirs() }

    @Test
    fun `keeps a normal file name`() {
        assertEquals("photo.jpg", WorkspaceImporter.safeName("photo.jpg"))
        assertEquals("README.md", WorkspaceImporter.safeName("README.md"))
    }

    @Test
    fun `strips directories from unix and windows paths`() {
        assertEquals("passwd", WorkspaceImporter.safeName("../../etc/passwd"))
        assertEquals("file.txt", WorkspaceImporter.safeName("C:\\Users\\me\\file.txt"))
        assertEquals("name.txt", WorkspaceImporter.safeName("/sdcard/Download/name.txt"))
    }

    @Test
    fun `replaces characters forbidden on shared storage`() {
        assertEquals("a_b_c_d_e_f_.txt", WorkspaceImporter.safeName("a:b*c?d\"e<f>.txt"))
    }

    @Test
    fun `replaces control characters`() {
        assertEquals("a_b.txt", WorkspaceImporter.safeName("a\u0000b.txt"))
    }

    @Test
    fun `falls back to file for blank and dot-only names`() {
        assertEquals("file", WorkspaceImporter.safeName(null))
        assertEquals("file", WorkspaceImporter.safeName(""))
        assertEquals("file", WorkspaceImporter.safeName("   "))
        assertEquals("file", WorkspaceImporter.safeName("..."))
    }

    @Test
    fun `truncates very long names`() {
        val long = "x".repeat(400) + ".txt"
        assertTrue(WorkspaceImporter.safeName(long).length <= 120)
    }

    @Test
    fun `uniqueTarget keeps a free name`() {
        val dir = tempDir()
        val target = WorkspaceImporter.uniqueTarget(dir, "note.txt")
        assertEquals(File(dir, "note.txt"), target)
        assertFalse(target.exists())
    }

    @Test
    fun `uniqueTarget adds a numeric suffix`() {
        val dir = tempDir()
        File(dir, "note.txt").writeText("one")
        assertEquals(File(dir, "note (1).txt"), WorkspaceImporter.uniqueTarget(dir, "note.txt"))
    }

    @Test
    fun `uniqueTarget skips taken suffixes`() {
        val dir = tempDir()
        File(dir, "note.txt").writeText("one")
        File(dir, "note (1).txt").writeText("two")
        assertEquals(File(dir, "note (2).txt"), WorkspaceImporter.uniqueTarget(dir, "note.txt"))
    }

    @Test
    fun `uniqueTarget handles names without extension`() {
        val dir = tempDir()
        File(dir, "README").writeText("one")
        assertEquals(File(dir, "README (1)"), WorkspaceImporter.uniqueTarget(dir, "README"))
    }

    @Test
    fun `uniqueTarget handles dotfiles`() {
        val dir = tempDir()
        File(dir, ".env").writeText("one")
        assertEquals(File(dir, ".env (1)"), WorkspaceImporter.uniqueTarget(dir, ".env"))
    }
}

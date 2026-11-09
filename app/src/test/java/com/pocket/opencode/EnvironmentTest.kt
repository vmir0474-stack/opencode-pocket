package com.pocket.opencode

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentTest {
    private val notePath =
        "/data/user/0/com.pocket.opencode/files/opencode/home/config/opencode/pocket-environment.md"

    private fun report(
        available: List<String> = listOf("sh", "curl", "rg", "pocket-rs"),
        missing: List<String> = listOf("git", "node", "python3"),
        storage: String = "none",
        media: Boolean = false,
        battery: Boolean = false,
        root: Boolean = false,
    ) = EnvReport(
        sdk = 30,
        release = "11",
        abi = "arm64-v8a",
        available = available.map { EnvCommand(it, "/system/bin/$it") },
        missing = missing,
        storageMode = storage,
        mediaAccess = media,
        batteryExempt = battery,
        rootBinary = root,
        workspace = "/sdcard/OpenCode",
        serverPath = "/data/tools:/system/bin",
        bridgeUrl = "http://127.0.0.1:4097",
        bridgeTokenFile = "/data/user/0/com.pocket.opencode/files/opencode/debug/token",
    )

    @Test
    fun `instructions list available and missing commands`() {
        val text = Environment.instructions(report())
        assertTrue(text.contains("sh"))
        assertTrue(text.contains("rg"))
        assertTrue(text.contains("Недоступно: git, node, python3"))
        assertTrue(text.contains("/sdcard/OpenCode"))
    }

    @Test
    fun `instructions explain rootless environment`() {
        val text = Environment.instructions(report())
        assertTrue(text.contains("`su` не найден"))
        assertTrue(text.contains("Root, Debian chroot, apt и systemd недоступны"))
        assertFalse(text.contains("`su` в системе есть"))
    }

    @Test
    fun `instructions mention su when binary exists`() {
        val text = Environment.instructions(report(root = true))
        assertTrue(text.contains("`su` в системе есть"))
        assertFalse(text.contains("`su` не найден"))
    }

    @Test
    fun `instructions describe storage and battery state`() {
        val blocked = Environment.instructions(report())
        assertTrue(blocked.contains("доступа нет"))
        assertTrue(blocked.contains("оптимизация включена"))

        val ready = Environment.instructions(
            report(storage = "all_files", battery = true)
        )
        assertTrue(ready.contains("доступ ко всем файлам выдан"))
        assertTrue(ready.contains("исключено из оптимизации"))
    }

    @Test
    fun `instructions describe media access when files access is missing`() {
        val noMedia = Environment.instructions(report())
        assertTrue(noMedia.contains("Медиа: чтение фото/видео/аудио не разрешено"))

        val media = Environment.instructions(report(media = true))
        assertTrue(media.contains("Медиа: разрешено чтение фото/видео/аудио"))
    }

    @Test
    fun `instructions skip media note when all files access is granted`() {
        val text = Environment.instructions(report(storage = "all_files", media = true))
        assertFalse(text.contains("Медиа:"))
    }

    @Test
    fun `instructions offer replacements for missing commands`() {
        val text = Environment.instructions(report())
        assertTrue(text.contains("`zip` → `tar"))
        assertTrue(text.contains("`wget` → `curl -L -o"))
        assertTrue(text.contains("`git` замены нет"))
    }

    @Test
    fun `instructions explain app launching through the bridge`() {
        val text = Environment.instructions(report())
        assertTrue(text.contains("\"name\":\"apps\""))
        assertTrue(text.contains("\"name\":\"launch\""))
        assertTrue(text.contains("/data/user/0/com.pocket.opencode/files/opencode/debug/token"))
        assertTrue(text.contains("http://127.0.0.1:4097"))
        assertTrue(text.contains("Android запрещает фоновый запуск"))
    }

    @Test
    fun `merge adds path without dropping user instructions`() {
        val merged = Environment.mergeInstructions(
            """{"model":"anthropic/claude","instructions":["USER.md"]}""",
            notePath,
        )
        assertNotNull(merged)
        val root = JSONObject(merged!!)
        val instructions = root.getJSONArray("instructions")
        assertEquals(2, instructions.length())
        assertEquals("USER.md", instructions.getString(0))
        assertEquals(notePath, instructions.getString(1))
        assertEquals("anthropic/claude", root.getString("model"))
    }

    @Test
    fun `merge is idempotent`() {
        val first = Environment.mergeInstructions("""{"model":"x"}""", notePath)!!
        val second = Environment.mergeInstructions(first, notePath)!!
        val instructions = JSONObject(second).getJSONArray("instructions")
        assertEquals(1, instructions.length())
        assertEquals(notePath, instructions.getString(0))
    }

    @Test
    fun `merge converts single string instruction`() {
        val merged = Environment.mergeInstructions(
            """{"instructions":"USER.md"}""",
            notePath,
        )
        assertNotNull(merged)
        val instructions = JSONObject(merged!!).getJSONArray("instructions")
        assertEquals(2, instructions.length())
    }

    @Test
    fun `merge keeps unexpected instructions type untouched`() {
        assertNull(Environment.mergeInstructions("""{"instructions":{"a":1}}""", notePath))
    }

    @Test
    fun `merge refuses broken json`() {
        assertNull(Environment.mergeInstructions("{", notePath))
    }

    @Test
    fun `registered detects exact path only`() {
        val text = """{"instructions":["$notePath"]}"""
        assertTrue(Environment.instructionsRegistered(text, notePath))
        assertFalse(Environment.instructionsRegistered(text, notePath + ".bak"))
        assertFalse(Environment.instructionsRegistered("""{"model":"x"}""", notePath))
    }

    @Test
    fun `report storage access follows mode`() {
        assertFalse(report(storage = "none").storageAccess)
        assertTrue(report(storage = "legacy").storageAccess)
        assertTrue(report(storage = "all_files").storageAccess)
    }
}

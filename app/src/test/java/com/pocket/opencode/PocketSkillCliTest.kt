package com.pocket.opencode

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PocketSkillCliTest {
    @Test
    fun `wrapper finds the bridge and covers every subcommand`() {
        val token = File("/data/user/0/com.pocket.opencode/files/opencode/debug/token")
        val script = Toolchain.skillScript(token)

        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains(token.absolutePath))
        assertTrue(script.contains("4097 4098 4099 4100"))
        for (command in listOf("search", "install", "update", "remove", "list")) {
            assertTrue("нет действия skill-$command", script.contains("skill-$command"))
        }
        assertTrue(script.contains("--project"))
        assertTrue(script.contains("--overwrite"))
        assertTrue(script.contains("--all"))
    }

    @Test
    fun `wrapper is valid POSIX shell`() {
        val file = File.createTempFile("pocket-skill", ".sh")
        try {
            file.writeText(Toolchain.skillScript(File("/tmp/opencode/debug/token")))
            val process = ProcessBuilder("/bin/sh", "-n", file.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(output, process.waitFor() == 0)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `instructions explain the skill command and its rules`() {
        val report = EnvReport(
            sdk = 30,
            release = "11",
            abi = "arm64-v8a",
            available = listOf(
                EnvCommand("sh", "/system/bin/sh"),
                EnvCommand("curl", "/system/bin/curl"),
                EnvCommand("rg", "/data/tools/rg"),
                EnvCommand("pocket-skill", "/data/tools/pocket-skill"),
            ),
            missing = listOf("git", "node"),
            storageMode = "legacy",
            mediaAccess = false,
            batteryExempt = true,
            rootBinary = false,
            workspace = "/sdcard/OpenCode",
            serverPath = "/data/tools:/system/bin",
            bridgeUrl = "http://127.0.0.1:4097",
            bridgeTokenFile = "/data/token",
        )
        val text = Environment.instructions(report)
        assertTrue(text.contains("pocket-skill install"))
        assertTrue(text.contains("pocket-skill update --all"))
        assertTrue(text.contains("--overwrite"))
        assertTrue(text.contains("reload"))
    }
}

package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallerTest {
    private fun info(name: String) = UpdateInfo(
        tag = "v$name",
        versionName = name,
        versionCode = 20,
        apkUrl = "https://example.test/app.apk",
        size = 10,
        sha256 = "abc",
        notes = "",
    )

    @Test
    fun `accepts only the expected digest`() {
        assertTrue(UpdateInstaller.digestMatches("abcdef", "abcdef"))
        assertTrue(UpdateInstaller.digestMatches("ABCDEF", "abcdef"))
        assertFalse(UpdateInstaller.digestMatches("abcdef", "abcdee"))
        assertFalse(UpdateInstaller.digestMatches("", ""))
    }

    @Test
    fun `builds a safe apk file name`() {
        assertEquals("opencode-pocket-0.8.0.apk", UpdateInstaller.apkFileName(info("0.8.0")))
        assertEquals(
            "opencode-pocket-0.8.0-evil.apk",
            UpdateInstaller.apkFileName(info("0.8.0/../evil"))
        )
        assertEquals(UpdateInstaller.APK_NAME, UpdateInstaller.apkFileName(info("///")))
    }
}

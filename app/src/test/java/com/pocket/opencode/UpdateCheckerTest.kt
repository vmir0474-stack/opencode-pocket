package com.pocket.opencode

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `compares semantic versions`() {
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.9") > 0)
        assertEquals(0, UpdateChecker.compareVersions("v0.8.0", "0.8.0"))
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.0-beta") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0-beta.2", "1.0.0-beta.1") > 0)
        assertTrue(UpdateChecker.compareVersions("0.8", "0.8.0") == 0)
    }

    private fun release(
        tag: String = "v0.8.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        withVersionJson: Boolean = true,
    ): String {
        val assets = mutableListOf(
            """{"name":"opencode-pocket-0.8.0.apk","size":52428800,
               "browser_download_url":"https://example.test/app.apk",
               "digest":"sha256:abcdef0123456789"}"""
        )
        if (withVersionJson) {
            assets += """{"name":"version.json","size":64,
                "browser_download_url":"https://example.test/version.json"}"""
        }
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,
            "body":"Заметки релиза","assets":[${assets.joinToString(",")}]}"""
    }

    private fun fetcher(body: String): ReleaseFetcher = ReleaseFetcher { url ->
        when {
            url.endsWith("version.json") -> """{"versionCode":20,"versionName":"0.8.0"}"""
            else -> body
        }
    }

    @Test
    fun `returns a newer release with digest and version json`() = runBlocking {
        val info = UpdateChecker.check("0.7.0", 19, fetcher(release()))
        requireNotNull(info)
        assertEquals("v0.8.0", info.tag)
        assertEquals("0.8.0", info.versionName)
        assertEquals(20, info.versionCode)
        assertEquals("abcdef0123456789", info.sha256)
        assertEquals(52428800L, info.size)
        assertEquals("Заметки релиза", info.notes)
    }

    @Test
    fun `skips draft and prerelease`() = runBlocking {
        assertNull(UpdateChecker.check("0.7.0", 19, fetcher(release(draft = true))))
        assertNull(UpdateChecker.check("0.7.0", 19, fetcher(release(prerelease = true))))
    }

    @Test
    fun `ignores the same or older release`() = runBlocking {
        assertNull(UpdateChecker.check("0.8.0", 20, fetcher(release())))
        assertNull(UpdateChecker.check("0.9.0", 21, fetcher(release())))
    }

    @Test
    fun `falls back to tag when version json is missing`() = runBlocking {
        val info = UpdateChecker.check(
            "0.7.0",
            19,
            fetcher(release(withVersionJson = false)),
        )
        requireNotNull(info)
        assertEquals("0.8.0", info.versionName)
        assertEquals(0, info.versionCode)
        assertEquals("abcdef0123456789", info.sha256)
    }
}

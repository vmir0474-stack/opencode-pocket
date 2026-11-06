package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ServerPathsTest {
    @Test
    fun `builds private xdg layout`() {
        val p = ServerPaths.under(File("/data/app/files/opencode"), File("/sdcard/OpenCode"))
        assertEquals(File("/data/app/files/opencode/home"), p.home)
        assertEquals(File("/data/app/files/opencode/home/data"), p.data)
        assertEquals(File("/data/app/files/opencode/home/config"), p.config)
        assertEquals(File("/data/app/files/opencode/home/cache"), p.cache)
        assertEquals(File("/data/app/files/opencode/logs"), p.logs)
        assertEquals(File("/data/app/files/opencode/home/cache/tmp"), p.tmp)
        assertEquals(File("/sdcard/OpenCode"), p.workspace)
        assertEquals(File("/data/app/files/opencode/home/data/opencode/opencode.db"), p.database)
    }
}

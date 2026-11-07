package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ServerEnvTest {
    @Test
    fun `builds isolated xdg environment`() {
        val p = ServerPaths.under(File("/app/files/opencode"), File("/sdcard/OpenCode"))
        val env = ServerEnv.build(p)
        assertEquals(p.home.absolutePath, env["HOME"])
        assertEquals(p.data.absolutePath, env["XDG_DATA_HOME"])
        assertEquals(p.config.absolutePath, env["XDG_CONFIG_HOME"])
        assertEquals(p.cache.absolutePath, env["XDG_CACHE_HOME"])
        assertEquals(p.tmp.absolutePath, env["TMPDIR"])
        assertEquals("/system/bin:/system/xbin", env["PATH"])
        assertEquals("C.UTF-8", env["LANG"])
        assertEquals(null, env["LD_LIBRARY_PATH"])
    }

    @Test
    fun `puts bundled tools and native libraries first`() {
        val p = ServerPaths.under(File("/app/files/opencode"), File("/sdcard/OpenCode"))
        val env = ServerEnv.build(
            p,
            toolsDir = File("/app/files/opencode/tools"),
            nativeLibraryDir = File("/app/lib/arm64"),
        )
        assertEquals("/app/files/opencode/tools:/system/bin:/system/xbin", env["PATH"])
        assertEquals("/app/lib/arm64", env["LD_LIBRARY_PATH"])
    }
}

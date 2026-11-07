package com.pocket.opencode

import java.io.File

/**
 * Окружение дочернего процесса OpenCode. HOME и все XDG-каталоги указывают
 * внутрь приватной папки приложения, чтобы сервер не трогал Termux и не
 * зависел от чужих настроек.
 */
object ServerEnv {
    fun build(
        paths: ServerPaths,
        toolsDir: File? = null,
        nativeLibraryDir: File? = null,
    ): Map<String, String> = buildMap {
        put("HOME", paths.home.absolutePath)
        put("XDG_DATA_HOME", paths.data.absolutePath)
        put("XDG_CONFIG_HOME", paths.config.absolutePath)
        put("XDG_CACHE_HOME", paths.cache.absolutePath)
        put("TMPDIR", paths.tmp.absolutePath)
        // toolsDir даёт серверу встроенный `rg`; PATH наследуется всеми
        // дочерними процессами инструментов (extendEnv).
        put(
            "PATH",
            listOfNotNull(toolsDir?.absolutePath, "/system/bin", "/system/xbin").joinToString(":"),
        )
        // rg линкуется с libpcre2-8.so, которая лежит рядом в nativeLibraryDir.
        nativeLibraryDir?.let { put("LD_LIBRARY_PATH", it.absolutePath) }
        put("LANG", "C.UTF-8")
    }
}

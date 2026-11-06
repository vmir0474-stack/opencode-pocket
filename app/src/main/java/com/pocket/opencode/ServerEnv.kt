package com.pocket.opencode

/**
 * Окружение дочернего процесса OpenCode. HOME и все XDG-каталоги указывают
 * внутрь приватной папки приложения, чтобы сервер не трогал Termux и не
 * зависел от чужих настроек.
 */
object ServerEnv {
    fun build(paths: ServerPaths): Map<String, String> = mapOf(
        "HOME" to paths.home.absolutePath,
        "XDG_DATA_HOME" to paths.data.absolutePath,
        "XDG_CONFIG_HOME" to paths.config.absolutePath,
        "XDG_CACHE_HOME" to paths.cache.absolutePath,
        "TMPDIR" to paths.tmp.absolutePath,
        "PATH" to "/system/bin:/system/xbin",
        "LANG" to "C.UTF-8",
    )
}

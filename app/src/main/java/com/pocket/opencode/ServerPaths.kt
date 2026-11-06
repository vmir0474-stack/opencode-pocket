package com.pocket.opencode

import android.content.Context
import java.io.File

enum class ServerMode { EMBEDDED, EXTERNAL }

enum class EmbeddedState { STOPPED, STARTING, RUNNING, ERROR }

/**
 * Каталоги встроенного OpenCode. Всё приватное живёт в files/opencode,
 * рабочий каталог — отдельно, обычно /sdcard/OpenCode.
 */
data class ServerPaths(
    val root: File,
    val home: File,
    val data: File,
    val config: File,
    val cache: File,
    val logs: File,
    val tmp: File,
    val workspace: File,
) {
    val database: File get() = File(data, "opencode/opencode.db")

    companion object {
        fun under(root: File, workspace: File): ServerPaths {
            val home = File(root, "home")
            val cache = File(home, "cache")
            return ServerPaths(
                root = root,
                home = home,
                data = File(home, "data"),
                config = File(home, "config"),
                cache = cache,
                logs = File(root, "logs"),
                tmp = File(cache, "tmp"),
                workspace = workspace,
            )
        }

        fun forContext(context: Context, workspace: File): ServerPaths =
            under(File(context.filesDir, "opencode"), workspace)
    }
}

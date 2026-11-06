package com.pocket.opencode

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/**
 * Журнал отладки: память для UI, NDJSON на диск, зеркало в общий каталог
 * при наличии доступа. Пароли и ключи вычищаются в [Redaction].
 */
object DebugLog {
    private const val FILE_MAX_BYTES = 1L * 1024 * 1024

    private val ring = DebugRing(500)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "debug-log-writer").apply { isDaemon = true }
    }

    @Volatile private var eventFile: File? = null
    @Volatile private var mirrorFile: File? = null

    fun init(context: Context) {
        val dir = File(context.filesDir, "opencode/debug")
        runCatching { dir.mkdirs() }
        eventFile = File(dir, "events.ndjson")
        mirrorFile = if (Workspace.hasAllFilesAccess(context)) {
            val shared = File("/sdcard/OpenCode/.debug")
            if (runCatching { shared.mkdirs() }.getOrDefault(false) || shared.isDirectory) {
                File(shared, "events.ndjson")
            } else {
                null
            }
        } else {
            null
        }
        event("debug", "journal started")
    }

    fun event(kind: String, message: String, data: Map<String, String> = emptyMap()) {
        val event = DebugEvent(System.currentTimeMillis(), kind, message, data)
        ring.add(event)
        val line = event.toLine()
        writer.execute {
            append(eventFile, line)
            append(mirrorFile, line)
        }
    }

    fun error(kind: String, throwable: Throwable) {
        val frames = throwable.stackTrace.take(6).joinToString("\n") { "  at $it" }
        event(
            kind,
            throwable.toString(),
            mapOf("stack" to frames.ifBlank { "no stack" })
        )
    }

    fun recent(limit: Int = 100): List<DebugEvent> = ring.recent(limit)

    fun clear() {
        ring.clear()
        writer.execute {
            runCatching { eventFile?.delete() }
            runCatching { mirrorFile?.delete() }
        }
    }

    fun file(): File? = eventFile

    private fun append(target: File?, line: String) {
        target ?: return
        runCatching {
            target.parentFile?.mkdirs()
            if (target.isFile && target.length() > FILE_MAX_BYTES) {
                val rotated = File(target.parentFile, target.name + ".1")
                rotated.delete()
                target.renameTo(rotated)
            }
            target.appendText(line + "\n")
        }
    }
}

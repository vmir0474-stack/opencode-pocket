package com.pocket.opencode

import android.content.Context
import android.util.LruCache
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Мост к нативному `pocket-rs`.
 *
 * Бинарник лежит в nativeLibraryDir под именем `libpocket_rs.so` (Android
 * распаковывает туда только `*.so`), а в app-owned каталоге создаётся
 * symlink `pocket-rs` — та же схема, что у `rg`. Все команды изолированы:
 * при ошибке, таймауте или отсутствии бинарника UI откатывается к
 * обычному виду.
 */
object RustTools {
    private const val LIB_NAME = "libpocket_rs.so"
    private const val BIN_NAME = "pocket-rs"

    @Volatile private var binary: File? = null
    @Volatile private var checked = false
    private val lock = Any()

    private val diffCache = LruCache<String, RustDiff>(32)
    private val highlightCache = LruCache<String, List<List<RustSpan>>>(24)

    fun ensure(context: Context): File? {
        binary?.let { return it }
        if (checked) return binary
        synchronized(lock) {
            binary?.let { return it }
            if (checked) return binary
            checked = true

            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            val source = File(nativeDir, LIB_NAME)
            if (!source.isFile) {
                DebugLog.event("rust", "pocket-rs missing", mapOf("dir" to nativeDir.absolutePath))
                return null
            }
            val dir = File(File(context.filesDir, "opencode"), "tools")
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return null

            val link = File(dir, BIN_NAME)
            val linked = runCatching {
                Files.deleteIfExists(link.toPath())
                Files.createSymbolicLink(link.toPath(), source.toPath())
                if (!link.canExecute()) link.setExecutable(true)
                true
            }.getOrElse {
                DebugLog.event("rust", "symlink failed", mapOf("error" to (it.message ?: it.toString())))
                false
            }
            if (!linked) return null

            // Запуск `pocket-rs version` на холодном старте стоит ~150 мс.
            // Файл меняется только с обновлением APK, поэтому кэшируем
            // успешную проверку и пропускаем запуск.
            val marker = File(dir, ".pocket-rs-ok")
            val signature = "${source.absolutePath}:${source.length()}:${source.lastModified()}"
            if (link.canExecute()) {
                val cached = runCatching { marker.readText() }.getOrNull()
                if (cached != null && cached.substringBefore('\n') == signature) {
                    binary = link
                    DebugLog.event(
                        "rust",
                        "pocket-rs ready",
                        mapOf(
                            "path" to link.absolutePath,
                            "version" to cached.substringAfter('\n', "").trim().take(80),
                            "cached" to "true",
                        ),
                    )
                    return link
                }
            }

            val probe = rawExec(link, listOf("version"), null, 4_000)
            if (probe != null && probe.contains("pocket-rs")) {
                binary = link
                val version = probe.lineSequence().first().take(80)
                runCatching { marker.writeText(signature + "\n" + version) }
                DebugLog.event(
                    "rust",
                    "pocket-rs ready",
                    mapOf(
                        "path" to link.absolutePath,
                        "version" to version,
                    ),
                )
                return link
            }
            runCatching { marker.delete() }
            DebugLog.event("rust", "pocket-rs self-test failed")
            return null
        }
    }

    fun find(context: Context, root: String, query: String, limit: Int = 12): List<FileHit> {
        if (root.isBlank()) return emptyList()
        val output = run(
            context,
            listOf("find", "--root", root, "--query", query, "--limit", limit.coerceIn(1, 30).toString()),
            null,
            6_000,
        ) ?: return emptyList()
        return parseFileHits(output)
    }

    fun diff(context: Context, old: String, new: String): RustDiff? {
        if (old == new) return RustDiff(0, 0, false, emptyList())
        // JSON с экранированием не должен упереться в лимит stdin (2 МБ).
        if (old.length + new.length > 800_000) return null
        val key = "${old.length}:${new.length}:${old.hashCode()}:${new.hashCode()}"
        diffCache.get(key)?.let { return it }
        val payload = runCatching {
            JSONObject().put("old", old).put("new", new).toString()
        }.getOrNull() ?: return null
        val output = run(context, listOf("diff"), payload, 5_000) ?: return null
        val parsed = parseRustDiff(output) ?: return null
        diffCache.put(key, parsed)
        return parsed
    }

    fun highlight(context: Context, language: String, code: String): List<List<RustSpan>>? {
        if (code.isBlank() || code.length > 60_000) return null
        val lang = normalizeLanguage(language)
        val key = "$lang:${code.length}:${code.hashCode()}"
        highlightCache.get(key)?.let { return it }
        val output = run(context, listOf("highlight", "--lang", lang), code, 4_000) ?: return null
        val parsed = parseHighlight(output) ?: return null
        highlightCache.put(key, parsed)
        return parsed
    }

    private fun run(context: Context, args: List<String>, stdin: String?, timeoutMs: Long): String? {
        val bin = ensure(context) ?: return null
        return rawExec(bin, args, stdin, timeoutMs)
    }

    private fun rawExec(bin: File, args: List<String>, stdin: String?, timeoutMs: Long): String? {
        return runCatching {
            val builder = ProcessBuilder(listOf(bin.absolutePath) + args)
            builder.redirectErrorStream(true)
            val process = builder.start()
            try {
                if (stdin != null) {
                    process.outputStream.bufferedWriter().use { it.write(stdin) }
                } else {
                    process.outputStream.close()
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    DebugLog.event("rust", "timeout", mapOf("args" to args.take(2).joinToString(" ")))
                    null
                } else if (process.exitValue() != 0) {
                    DebugLog.event(
                        "rust",
                        "command failed",
                        mapOf(
                            "args" to args.take(2).joinToString(" "),
                            "output" to output.take(240),
                        ),
                    )
                    null
                } else {
                    output
                }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }.getOrElse {
            DebugLog.error("rust", it)
            null
        }
    }
}

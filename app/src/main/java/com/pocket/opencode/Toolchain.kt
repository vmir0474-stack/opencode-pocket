package com.pocket.opencode

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Встроенные CLI-инструменты сервера.
 *
 * Android (targetSdk 29+) запрещает исполнять ELF из app-data, но разрешает
 * запускать файлы из nativeLibraryDir. PackageManager распаковывает туда
 * только `*.so`, поэтому бинарник лежит как `librg.so`, а рядом в
 * app-owned каталоге создаётся symlink с обычным именем `rg` — opencode
 * ищет его через PATH. Цель symlink меняется после обновления APK, поэтому
 * ссылка пересоздаётся при каждом старте.
 */
object Toolchain {
    private const val RG_LIB = "librg.so"
    private const val PCRE_LIB = "libpcre2-8.so"
    private const val SELF_TEST_TIMEOUT_MS = 8_000L

    data class Status(
        val dir: File,
        val rg: File? = null,
        val version: String = "",
        val error: String = "",
        val pocketRust: File? = null,
    ) {
        val ok: Boolean get() = rg != null && error.isBlank()
    }

    fun toolsDir(paths: ServerPaths): File = File(paths.root, "tools")

    /**
     * Готовит каталог инструментов и проверяет, что `rg` действительно
     * запускается. Ошибка не валит сервер: grep просто станет недоступен.
     */
    fun ensure(context: Context, paths: ServerPaths): Status {
        val dir = toolsDir(paths)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val source = File(nativeDir, RG_LIB)
        if (!source.isFile) {
            return Status(dir, error = "нет $RG_LIB в ${nativeDir.absolutePath}")
        }
        val pcre = File(nativeDir, PCRE_LIB)
        if (!pcre.isFile) {
            return Status(dir, error = "нет $PCRE_LIB в ${nativeDir.absolutePath}")
        }
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            return Status(dir, error = "не удалось создать ${dir.absolutePath}")
        }
        val link = File(dir, "rg")
        runCatching { linkTo(link, source) }
            .onFailure { return Status(dir, error = "symlink: ${it.message}") }

        // Нативный Rust-инструмент: find/diff/highlight для чата.
        val rustSource = File(nativeDir, "libpocket_rs.so")
        val rustLink = File(dir, "pocket-rs")
        val pocketRust = if (rustSource.isFile) {
            runCatching { linkTo(rustLink, rustSource); rustLink }.getOrNull()
        } else {
            null
        }

        val test = selfTest(link, nativeDir)
        return if (test.first == 0) {
            Status(dir, link, test.second.trim(), "", pocketRust)
        } else {
            Status(dir, link, "", test.second.trim().ifBlank { "rg завершился с кодом ${test.first}" }, pocketRust)
        }
    }

    /** PATH для сервера: сначала наш `rg`, затем системные утилиты. */
    fun path(status: Status): String {
        val prefix = status.dir.absolutePath
        return "$prefix:/system/bin:/system/xbin"
    }

    private fun linkTo(link: File, target: File) {
        val linkPath = link.toPath()
        Files.deleteIfExists(linkPath)
        Files.createSymbolicLink(linkPath, target.toPath())
    }

    private fun selfTest(rg: File, nativeDir: File): Pair<Int, String> {
        return runCatching {
            val builder = ProcessBuilder(rg.absolutePath, "--version")
            builder.redirectErrorStream(true)
            builder.environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
            builder.environment()["PATH"] = "/system/bin:/system/xbin"
            val proc = builder.start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            if (!proc.waitFor(SELF_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
                return@runCatching -1 to "$out\ntimeout"
            }
            proc.exitValue() to out
        }.getOrElse { -1 to (it.message ?: it.toString()) }
    }
}

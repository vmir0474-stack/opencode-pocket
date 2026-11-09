package com.pocket.opencode

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

data class SkillEntry(
    val path: String,
    val directory: Boolean,
    val size: Long,
    val link: Boolean = false,
)

data class SkillLimits(
    val maxFiles: Int = 500,
    val maxTotalBytes: Long = 20L * 1024 * 1024,
    val maxFileBytes: Long = 5L * 1024 * 1024,
)

/**
 * Безопасная работа с tar.gz из GitHub. Скилы — чужой контент, поэтому
 * архив не распаковывается «как есть»: отбрасываются абсолютные пути,
 * `..`, симлинки и устройства, а размер ограничен.
 */
object SkillArchive {
    fun safePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\\')) return false
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
        return true
    }

    /** Первый проход по архиву: только имена и размеры, без записи на диск. */
    fun entries(tar: InputStream): List<SkillEntry> {
        val out = mutableListOf<SkillEntry>()
        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(tar))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                out += SkillEntry(
                    path = entry.name.trimEnd('/'),
                    directory = entry.isDirectory,
                    size = entry.size,
                    link = entry.isSymbolicLink || entry.isLink,
                )
            }
        }
        return out
    }

    /**
     * Каталоги, в которых лежит `SKILL.md`. Если указан конкретный скил,
     * сначала ищем точный путь, затем имя каталога. Пустой результат при
     * указанном имени означает «не найдено», а не «ставь всё».
     */
    fun skillDirs(entries: List<SkillEntry>, skill: String?): List<String> {
        val dirs = entries.asSequence()
            .filter { !it.directory && !it.link && it.path.endsWith("/SKILL.md") }
            .map { it.path.removeSuffix("/SKILL.md") }
            .distinct()
            .toList()
        val wanted = skill?.trim()?.trim('/')?.takeIf { it.isNotBlank() } ?: return dirs
        val exact = dirs.filter { it == wanted || it.endsWith("/$wanted") }
        if (exact.isNotEmpty()) return exact
        return dirs.filter { it.substringAfterLast('/') == wanted }
    }

    /** Все каталоги со `SKILL.md` — для поиска по frontmatter-имени. */
    fun allSkillDirs(entries: List<SkillEntry>): List<String> = skillDirs(entries, null)

    /**
     * Второй проход: пишет только выбранные каталоги. Возвращает число
     * файлов. Ошибка при превышении лимитов оставляет распаковку в temp,
     * которую вызывающий удаляет.
     */
    fun extract(
        tar: InputStream,
        dest: File,
        selected: Set<String>,
        limits: SkillLimits = SkillLimits(),
    ): Int {
        val canonicalDest = dest.canonicalFile
        var files = 0
        var total = 0L
        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(tar))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val path = entry.name.trimEnd('/')
                val skip = entry.isDirectory ||
                    entry.isSymbolicLink ||
                    entry.isLink ||
                    !safePath(path) ||
                    selected.none { path == it || path.startsWith("$it/") } ||
                    entry.size < 0 ||
                    entry.size > limits.maxFileBytes
                if (skip) {
                    continue
                }
                if (files + 1 > limits.maxFiles || total + entry.size > limits.maxTotalBytes) {
                    error(
                        "Скил слишком большой: больше ${limits.maxFiles} файлов " +
                            "или ${limits.maxTotalBytes / 1024 / 1024} МБ"
                    )
                }
                val target = File(dest, path)
                val canonicalTarget = target.canonicalFile
                if (canonicalTarget.path != canonicalDest.path &&
                    !canonicalTarget.path.startsWith(canonicalDest.path + File.separator)
                ) {
                    continue
                }
                canonicalTarget.parentFile?.mkdirs()
                canonicalTarget.outputStream().use { out -> input.copyTo(out) }
                files++
                total += entry.size
            }
        }
        return files
    }
}

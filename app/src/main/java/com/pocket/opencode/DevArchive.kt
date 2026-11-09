package com.pocket.opencode

import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Распаковка rootfs без root-прав.
 *
 * tar-архив Ubuntu содержит обычные файлы, каталоги, симлинки и hardlink.
 * Всё это создаётся от имени приложения, а режимы доступа выставляются
 * через `Os.chmod`. Архив проверяется по SHA-256 до распаковки, но путь
 * каждой записи всё равно нормализуется: запись не должна выйти за
 * пределы каталога установки.
 */
object DevArchive {

    /**
     * Приводит имя записи tar к безопасному относительному пути.
     * Возвращает null для абсолютных путей, `..` и пустых имён.
     */
    fun safeRelative(name: String): String? {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/")) return null
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    /** Режим файла из tar: только биты прав, без типа записи. */
    fun permissions(raw: Int): Int = raw and 0x1FF

    /**
     * Распаковывает gzip-tar в [destination]. Каталог должен быть пустым.
     * Возвращает число записей. Прогресс — по количеству записей.
     */
    fun extract(archive: File, destination: File, onEntry: (Int) -> Unit = {}): Int {
        if (!destination.isDirectory && !destination.mkdirs()) {
            throw IOException("Не удалось создать каталог: ${destination.absolutePath}")
        }
        val root = destination.canonicalFile
        val rootPrefix = root.absolutePath + File.separator
        val deferredModes = mutableListOf<Pair<File, Int>>()
        var entries = 0

        BufferedInputStream(FileInputStream(archive)).use { raw ->
            GzipCompressorInputStream(raw).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val current = entry
                        val relative = safeRelative(current.name)
                            ?: throw IOException("Небезопасный путь в архиве: ${current.name}")
                        val target = File(root, relative)
                        // canonicalPath разрешает уже созданные симлинки.
                        if (target.canonicalPath != root.absolutePath &&
                            !target.canonicalPath.startsWith(rootPrefix)
                        ) {
                            throw IOException("Запись выходит за пределы rootfs: ${current.name}")
                        }

                        when {
                            current.isDirectory -> {
                                target.mkdirs()
                                deferredModes += target to permissions(current.mode)
                            }

                            current.isSymbolicLink -> {
                                target.parentFile?.mkdirs()
                                Files.deleteIfExists(target.toPath())
                                val link = current.linkName
                                    ?: throw IOException("Симлинк без цели: ${current.name}")
                                Files.createSymbolicLink(target.toPath(), Paths.get(link))
                            }

                            current.isLink -> {
                                target.parentFile?.mkdirs()
                                Files.deleteIfExists(target.toPath())
                                val sourceName = safeRelative(current.linkName ?: "")
                                    ?: throw IOException("Hardlink без цели: ${current.name}")
                                val source = File(root, sourceName)
                                runCatching { Files.createLink(target.toPath(), source.toPath()) }
                                    .onFailure {
                                        source.copyTo(target, overwrite = true)
                                    }
                            }

                            else -> {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output -> tar.copyTo(output) }
                                setMode(target, permissions(current.mode))
                            }
                        }
                        entries++
                        onEntry(entries)
                        entry = tar.nextEntry
                    }
                }
            }
        }

        // Права каталогов выставляем после распаковки: иначе каталог 0555
        // помешал бы записать в него файлы.
        deferredModes.forEach { (dir, mode) -> setMode(dir, mode) }
        return entries
    }

    private fun setMode(file: File, mode: Int) {
        if (mode == 0) return
        runCatching { Os.chmod(file.absolutePath, mode) }
    }
}

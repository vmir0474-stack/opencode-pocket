package com.pocket.opencode

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Собирает tar.gz в памяти, чтобы тесты не ходили в сеть. */
fun tarGz(files: Map<String, String>, symlinks: Map<String, String> = emptyMap()): ByteArray {
    val out = ByteArrayOutputStream()
    GzipCompressorOutputStream(out).use { gzip ->
        TarArchiveOutputStream(gzip).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for ((path, content) in files) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val entry = TarArchiveEntry(path).apply { size = bytes.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            for ((path, target) in symlinks) {
                val entry = TarArchiveEntry(path, TarConstants.LF_SYMLINK)
                entry.linkName = target
                tar.putArchiveEntry(entry)
                tar.closeArchiveEntry()
            }
        }
    }
    return out.toByteArray()
}

class FakeSkillFetcher(
    private val bytesByUrl: Map<String, ByteArray> = emptyMap(),
    private val failUrls: Set<String> = emptySet(),
) : SkillFetcher {
    val requested = mutableListOf<String>()

    override suspend fun bytes(url: String, maxBytes: Long): ByteArray {
        requested += url
        if (url in failUrls) error("offline: $url")
        return bytesByUrl[url] ?: error("нет фикстуры для $url")
    }

    override suspend fun download(url: String, dest: File, maxBytes: Long): Long {
        val data = bytes(url, maxBytes)
        dest.parentFile?.mkdirs()
        dest.writeBytes(data)
        return data.size.toLong()
    }
}

package com.pocket.opencode

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class ImportResult(
    val imported: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
)

/**
 * Копирует файлы, выбранные через SAF, в рабочий каталог.
 *
 * Это единственный способ передать агенту произвольный файл без
 * MANAGE_EXTERNAL_STORAGE: content:// читается через ContentResolver,
 * а в проект попадает обычный файл, который сервер видит как свой.
 */
object WorkspaceImporter {
    private const val MAX_NAME = 120

    fun import(context: Context, dir: File, uris: List<Uri>): ImportResult {
        if (uris.isEmpty()) return ImportResult()
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            return ImportResult(failed = uris.map { displayName(context, it) ?: it.toString() })
        }
        val imported = mutableListOf<String>()
        val failed = mutableListOf<String>()
        uris.forEach { uri ->
            val name = safeName(displayName(context, uri) ?: uri.lastPathSegment)
            val target = uniqueTarget(dir, name)
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
                } ?: error("no stream")
            }.isSuccess
            if (ok) {
                imported += target.name
            } else {
                runCatching { target.delete() }
                failed += name
            }
        }
        return ImportResult(imported, failed)
    }

    /** Имя без путей и символов, недопустимых на общих хранилищах. */
    fun safeName(raw: String?): String {
        val base = (raw ?: "").substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString {
            base.forEach { ch ->
                append(if (ch.isISOControl() || ch in "/\\:*?\"<>|") '_' else ch)
            }
        }.trim().trim('.').take(MAX_NAME)
        return cleaned.ifBlank { "file" }
    }

    /** Не перезаписывает существующий файл: добавляет « (1)», « (2)» и т.д. */
    fun uniqueTarget(dir: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($index)$ext")
            index++
        }
        return candidate
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private const val DEFAULT_BUFFER = 64 * 1024
}

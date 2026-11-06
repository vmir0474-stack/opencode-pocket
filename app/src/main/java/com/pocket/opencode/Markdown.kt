package com.pocket.opencode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

internal sealed interface MdChunk {
    data class Prose(val text: String) : MdChunk
    data class Code(val lang: String, val text: String) : MdChunk
}

private val inlinePattern = Regex("(\\*\\*[^*\\n]+\\*\\*|`[^`\\n]+`)")

/** Мини-разметка: fenced code, **жирный**, `код`, заголовки и списки. */
internal fun markdownChunks(source: String): List<MdChunk> {
    if (source.isBlank()) return listOf(MdChunk.Prose(""))
    val out = mutableListOf<MdChunk>()
    val prose = StringBuilder()
    val lines = source.lines()
    var i = 0

    fun flush() {
        if (prose.isNotBlank()) out += MdChunk.Prose(prose.toString().trimEnd())
        prose.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            flush()
            val fence = trimmed.take(3)
            val lang = trimmed.removePrefix(fence).trim()
            val code = StringBuilder()
            i++
            while (i < lines.size) {
                val inner = lines[i]
                val innerTrim = inner.trimStart()
                if (innerTrim.startsWith(fence)) break
                code.appendLine(inner)
                i++
            }
            out += MdChunk.Code(lang, code.toString().trimEnd('\n'))
            if (i < lines.size) i++
            continue
        }
        prose.appendLine(line)
        i++
    }
    flush()
    return if (out.isEmpty()) listOf(MdChunk.Prose(source)) else out
}

internal fun proseAnnotated(source: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in inlinePattern.findAll(source)) {
        if (match.range.first > last) append(source.substring(last, match.range.first))
        val token = match.value
        if (token.startsWith("**")) {
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain
                )
            ) { append(token.removePrefix("**").removeSuffix("**")) }
        } else {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0xFF23253C),
                    color = Color(0xFFD9CFFF)
                )
            ) { append(token.removePrefix("`").removeSuffix("`")) }
        }
        last = match.range.last + 1
    }
    if (last < source.length) append(source.substring(last))
}

/** Подсветка unified diff: добавления, удаления, заголовки. */
internal fun diffAnnotated(patch: String): AnnotatedString = buildAnnotatedString {
    patch.lineSequence().forEach { line ->
        val color = when {
            line.startsWith("+++") || line.startsWith("---") -> Dim
            line.startsWith("@@") -> Accent
            line.startsWith("+") -> Mint
            line.startsWith("-") -> Danger
            line.startsWith("diff ") || line.startsWith("index ") -> Muted
            else -> TextMain
        }
        withStyle(SpanStyle(color = color)) {
            append(line)
            append('\n')
        }
    }
}

internal fun toolTitle(name: String): String = when (name.lowercase()) {
    "bash", "shell", "execute" -> "Команда"
    "read", "view", "cat" -> "Чтение файла"
    "write", "create" -> "Создание файла"
    "edit", "patch", "multiedit", "apply_patch" -> "Правка файла"
    "grep", "search" -> "Поиск по коду"
    "glob", "find" -> "Поиск файлов"
    "webfetch", "fetch", "websearch" -> "Запрос в сеть"
    "task", "agent" -> "Подагент"
    "todowrite", "todoread", "todo" -> "План задач"
    "list", "ls" -> "Список файлов"
    "question", "ask" -> "Вопрос"
    else -> name.replaceFirstChar { it.uppercase() }.ifBlank { "Инструмент" }
}

internal fun toolStatusLabel(status: String): Pair<String, Color> = when (status) {
    "running" -> "выполняется" to Warn
    "completed" -> "готово" to Mint
    "error", "failed" -> "ошибка" to Danger
    "streaming" -> "подготовка" to Accent
    else -> status to Muted
}

package com.pocket.opencode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.json.JSONArray
import org.json.JSONObject

data class FileHit(
    val path: String,
    val name: String,
    val score: Int = 0,
)

data class RustSpan(
    val start: Int,
    val end: Int,
    val color: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

data class RustDiffLine(
    val kind: Char,
    val oldLine: Int,
    val newLine: Int,
    val text: String,
)

data class RustDiff(
    val additions: Int,
    val deletions: Int,
    val truncated: Boolean,
    val lines: List<RustDiffLine>,
)

data class EditPatch(
    val path: String,
    val old: String = "",
    val new: String = "",
    val unified: String? = null,
)

data class MentionTrigger(val query: String, val at: Int)

data class TurnStats(
    val tools: Int,
    val files: List<String>,
    val commands: List<String>,
    val errors: Int,
    val edits: Int,
    val reads: Int,
    val searches: Int,
)

internal val EDIT_TOOLS = setOf(
    "edit", "patch", "multiedit", "apply_patch", "applypatch", "str_replace"
)

internal fun parseFileHits(raw: String): List<FileHit> {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val array = root.optJSONArray("files") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val path = item.optString("path")
            if (path.isBlank()) continue
            add(
                FileHit(
                    path = path,
                    name = item.optString("name").ifBlank { path.substringAfterLast('/') },
                    score = item.optInt("score"),
                )
            )
        }
    }
}

internal fun parseRustDiff(raw: String): RustDiff? {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (root.has("error")) return null
    val array = root.optJSONArray("lines") ?: return null
    val lines = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val kind = item.optString("k").firstOrNull() ?: 'c'
            add(
                RustDiffLine(
                    kind = kind,
                    oldLine = item.optInt("o"),
                    newLine = item.optInt("n"),
                    text = item.optString("t"),
                )
            )
        }
    }
    return RustDiff(
        additions = root.optInt("additions"),
        deletions = root.optInt("deletions"),
        truncated = root.optBoolean("truncated"),
        lines = lines,
    )
}

/**
 * Разбирает стандартный unified diff (как в `apply_patch`), чтобы показать
 * его теми же строками, что и результат Rust `diff`.
 */
internal fun parseUnifiedPatch(raw: String): RustDiff {
    val lines = mutableListOf<RustDiffLine>()
    var additions = 0
    var deletions = 0
    var oldLine = 0
    var newLine = 0
    var truncated = false

    raw.lineSequence().forEach { line ->
        if (lines.size >= 4_000) {
            truncated = true
            return@forEach
        }
        when {
            line.startsWith("***") -> Unit
            line.startsWith("@@") -> {
                val match = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@").find(line)
                oldLine = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                newLine = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                lines += RustDiffLine('h', 0, 0, line)
            }
            line.startsWith("+++") || line.startsWith("---") ||
                line.startsWith("diff ") || line.startsWith("index ") -> Unit
            line.startsWith("+") -> {
                additions++
                lines += RustDiffLine('a', 0, newLine, line.drop(1))
                newLine++
            }
            line.startsWith("-") -> {
                deletions++
                lines += RustDiffLine('d', oldLine, 0, line.drop(1))
                oldLine++
            }
            else -> {
                lines += RustDiffLine(
                    'c',
                    oldLine,
                    newLine,
                    line.removePrefix(" "),
                )
                oldLine++
                newLine++
            }
        }
    }

    return RustDiff(additions, deletions, truncated, lines)
}

internal fun parseHighlight(raw: String): List<List<RustSpan>>? {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (root.has("error")) return null
    val array = root.optJSONArray("lines") ?: return null
    return buildList {
        for (index in 0 until array.length()) {
            val line = array.optJSONArray(index) ?: JSONArray()
            add(
                buildList {
                    for (spanIndex in 0 until line.length()) {
                        val span = line.optJSONObject(spanIndex) ?: continue
                        add(
                            RustSpan(
                                start = span.optInt("s"),
                                end = span.optInt("e"),
                                color = span.optString("c"),
                                bold = span.optBoolean("b"),
                                italic = span.optBoolean("i"),
                            )
                        )
                    }
                }
            )
        }
    }
}

/**
 * Достаёт старую и новую версию правки из входа инструмента.
 * Возвращает пустой список для не-редактирующих инструментов, чтобы
 * вызывающая сторона могла показать обычный JSON.
 */
internal fun parseEditPatches(toolName: String, input: String): List<EditPatch> {
    if (toolName.lowercase() !in EDIT_TOOLS) return emptyList()
    val root = runCatching { JSONObject(input) }.getOrNull() ?: return emptyList()
    val out = mutableListOf<EditPatch>()

    fun add(item: JSONObject, fallbackPath: String?) {
        val path = item.optText("filePath")
            ?: item.optText("file_path")
            ?: item.optText("path")
            ?: fallbackPath
        val old = item.optText("oldString")
            ?: item.optText("old_string")
            ?: item.optText("old")
        val new = item.optText("newString")
            ?: item.optText("new_string")
            ?: item.optText("new")
        val unified = item.optText("patch")
            ?: item.optText("patchText")
            ?: item.optText("diff")
        val patchPath = path ?: unified?.let { text ->
            Regex("""\*\*\* (?:Update|Add|Delete) File: (.+)""")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        }
        when {
            old != null || new != null ->
                out += EditPatch(patchPath.orEmpty(), old.orEmpty(), new.orEmpty())
            unified != null ->
                out += EditPatch(patchPath.orEmpty(), unified = unified)
        }
    }

    add(root, null)
    val fallback = root.optText("filePath") ?: root.optText("file_path") ?: root.optText("path")
    for (key in listOf("edits", "files", "changes")) {
        val array = root.optJSONArray(key) ?: continue
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(it, fallback) }
        }
    }
    return out.distinctBy { it.path + ":" + it.old.hashCode() + ":" + it.new.hashCode() }.take(8)
}

/** Ищет незакрытый `@запрос` в конце ввода. */
internal fun mentionTrigger(text: String): MentionTrigger? {
    if (text.length > 4_000) return null
    val at = text.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !text[at - 1].isWhitespace()) return null
    val query = text.substring(at + 1)
    if (query.length > 80) return null
    if (query.any { it.isWhitespace() || it == '@' }) return null
    return MentionTrigger(query, at)
}

internal fun insertMentionRef(text: String, at: Int, path: String): String {
    if (at !in text.indices || text[at] != '@') return text
    return text.substring(0, at) + "@" + path + " "
}

/** Сводка последнего хода: всё, что агент сделал после сообщения пользователя. */
internal fun summarizeTurn(messages: List<UiMessage>): TurnStats? {
    val userIndex = messages.indexOfLast { it.role == UiRole.USER }
    if (userIndex < 0) return null
    val tools = messages.drop(userIndex + 1)
        .filter { it.role == UiRole.ASSISTANT }
        .flatMap { message -> message.blocks.filterIsInstance<UiBlock.Tool>() }
    if (tools.isEmpty()) return null

    val files = LinkedHashSet<String>()
    val commands = LinkedHashSet<String>()
    var errors = 0
    var edits = 0
    var reads = 0
    var searches = 0

    tools.forEach { tool ->
        val name = tool.name.lowercase()
        val input = runCatching { JSONObject(tool.input) }.getOrNull()
        val path = input?.let { obj ->
            listOf("filePath", "file_path", "path").firstNotNullOfOrNull { obj.optText(it) }
        }

        if (name in EDIT_TOOLS) {
            edits++
            path?.let { files += it }
            input?.optJSONArray("edits")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)
                        ?.optText("filePath")
                        ?.let { files += it }
                }
            }
        } else if (name == "write" || name == "create") {
            edits++
            path?.let { files += it }
        } else if (name == "read" || name == "view" || name == "cat") {
            reads++
            path?.let { files += it }
        } else if (name == "grep" || name == "glob" || name == "search" || name == "find") {
            searches++
        }

        if (name == "bash" || name == "shell" || name == "execute") {
            input?.optText("command")
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.take(120)
                ?.takeIf { it.isNotBlank() }
                ?.let { commands += it }
        }

        if (tool.status == "error" || tool.status == "failed" || !tool.error.isNullOrBlank()) {
            errors++
        }
    }

    return TurnStats(
        tools = tools.size,
        files = files.toList().take(8),
        commands = commands.toList().take(5),
        errors = errors,
        edits = edits,
        reads = reads,
        searches = searches,
    )
}

internal fun turnReport(stats: TurnStats, tokensIn: Long, tokensOut: Long, cost: Double): String = buildString {
    appendLine("Итог хода")
    appendLine("Инструментов: ${stats.tools} · файлов: ${stats.files.size} · ошибок: ${stats.errors}")
    if (stats.files.isNotEmpty()) {
        appendLine("Файлы:")
        stats.files.forEach { appendLine("· $it") }
    }
    if (stats.commands.isNotEmpty()) {
        appendLine("Команды:")
        stats.commands.forEach { appendLine("$ $it") }
    }
    appendLine("Токены: ${shortNumber(tokensIn)} → ${shortNumber(tokensOut)}")
    append("Стоимость сессии: $${String.format(java.util.Locale.US, "%.4f", cost)}")
}

internal fun normalizeLanguage(language: String): String = when (language.trim().lowercase()) {
    "kotlin" -> "kt"
    "kts" -> "kt"
    "typescript" -> "ts"
    "javascript" -> "js"
    "python" -> "py"
    "bash", "shell", "zsh" -> "sh"
    "c++" -> "cpp"
    "c#" -> "cs"
    "objective-c", "objc" -> "m"
    "yml" -> "yaml"
    "docker" -> "dockerfile"
    else -> language.trim().lowercase()
}

internal fun parseHexColor(value: String): Color? {
    val hex = value.removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

/**
 * Собирает подсвеченный текст из span-ов Rust. Возвращает null, если
 * разметка не совпала с текстом — тогда UI покажет обычный моноширинный код.
 */
internal fun highlightedAnnotated(code: String, lines: List<List<RustSpan>>?): AnnotatedString? {
    if (lines == null) return null
    val source = code.lines()
    if (lines.size != source.size) return null
    return buildAnnotatedString {
        source.forEachIndexed { index, line ->
            if (line.isNotEmpty()) {
                var cursor = 0
                lines[index].forEach { span ->
                    val start = span.start.coerceIn(0, line.length)
                    val end = span.end.coerceIn(start, line.length)
                    if (start > cursor) append(line.substring(cursor, start))
                    if (end > start) {
                        withStyle(
                            SpanStyle(
                                color = parseHexColor(span.color) ?: TextMain,
                                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                                fontStyle = if (span.italic) FontStyle.Italic else null,
                            )
                        ) {
                            append(line.substring(start, end))
                        }
                    }
                    if (end > cursor) cursor = end
                }
                if (cursor < line.length) append(line.substring(cursor))
            }
            if (index != source.lastIndex) append('\n')
        }
    }
}

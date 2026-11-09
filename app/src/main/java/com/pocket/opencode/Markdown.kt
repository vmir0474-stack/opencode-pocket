package com.pocket.opencode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/* ------------------------------------------------------------------ */
/*  Блочная разметка                                                   */
/* ------------------------------------------------------------------ */

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletList(val items: List<MdListItem>) : MdBlock
    data class OrderedList(val items: List<MdListItem>) : MdBlock
    data class Quote(val lines: List<String>) : MdBlock
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val align: List<MdAlign>
    ) : MdBlock

    data class Code(val lang: String, val text: String) : MdBlock
    data object Rule : MdBlock
}

internal enum class MdAlign { START, CENTER, END }

internal data class MdListItem(
    val text: String,
    val level: Int = 0,
    val number: Int? = null,
    val checked: Boolean? = null
)

private val fencePattern = Regex("^\\s{0,3}(```+|~~~+)\\s*(.*)$")
private val headingPattern = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
private val rulePattern = Regex("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
private val listItemPattern = Regex("^(\\s*)([-*+]|(\\d{1,9})[.)])\\s+(.*)$")
private val taskPattern = Regex("^\\[([ xX])]\\s+(.*)$")
private val tableCellPattern = Regex("^:?-{2,}:?$")

/** Разбирает Markdown на блоки: заголовки, списки, таблицы, цитаты, код. */
internal fun markdownBlocks(source: String): List<MdBlock> {
    if (source.isBlank()) return emptyList()
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    val out = mutableListOf<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) {
            i++
            continue
        }

        val fence = fencePattern.find(line)
        if (fence != null) {
            val marker = fence.groupValues[1]
            val lang = fence.groupValues[2].trim()
            val code = StringBuilder()
            i++
            while (i < lines.size) {
                val inner = lines[i]
                if (inner.trimStart().startsWith(marker)) {
                    i++
                    break
                }
                code.appendLine(inner)
                i++
            }
            out += MdBlock.Code(lang, code.toString().trimEnd('\n'))
            continue
        }

        val heading = headingPattern.find(line)
        if (heading != null) {
            out += MdBlock.Heading(
                heading.groupValues[1].length,
                heading.groupValues[2].trim()
            )
            i++
            continue
        }

        if (rulePattern.matches(line)) {
            out += MdBlock.Rule
            i++
            continue
        }

        if (line.contains('|') && i + 1 < lines.size && isTableDelimiter(lines[i + 1])) {
            val header = splitTableRow(line)
            val align = splitTableRow(lines[i + 1]).map { cell ->
                when {
                    cell.startsWith(":") && cell.endsWith(":") -> MdAlign.CENTER
                    cell.endsWith(":") -> MdAlign.END
                    else -> MdAlign.START
                }
            }
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                rows += splitTableRow(lines[i])
                i++
            }
            out += MdBlock.Table(header, rows, align)
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quote = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                quote += lines[i].trimStart().removePrefix(">").removePrefix(" ").trimEnd()
                i++
            }
            out += MdBlock.Quote(quote)
            continue
        }

        val item = parseListItem(line)
        if (item != null) {
            val ordered = item.number != null
            val items = mutableListOf(item)
            i++
            while (i < lines.size) {
                val next = lines[i]
                val nextItem = parseListItem(next)
                if (nextItem != null && (nextItem.number != null) == ordered) {
                    items += nextItem
                    i++
                    continue
                }
                if (next.isNotBlank() && next.startsWith("  ") && !startsBlock(lines, i)) {
                    val last = items.removeAt(items.lastIndex)
                    items += last.copy(text = last.text + " " + next.trim())
                    i++
                    continue
                }
                break
            }
            out += if (ordered) MdBlock.OrderedList(items) else MdBlock.BulletList(items)
            continue
        }

        val paragraph = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() &&
            (paragraph.isEmpty() || !startsBlock(lines, i))
        ) {
            paragraph += lines[i].trim()
            i++
        }
        if (paragraph.isNotEmpty()) {
            out += MdBlock.Paragraph(paragraph.joinToString(" "))
        } else {
            // Страховка от зацикливания на неожиданной конструкции.
            out += MdBlock.Paragraph(line.trim())
            i++
        }
    }
    return out
}

private fun startsBlock(lines: List<String>, index: Int): Boolean {
    val line = lines[index]
    if (line.isBlank()) return true
    val trimmed = line.trimStart()
    if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) return true
    if (headingPattern.containsMatchIn(line)) return true
    if (rulePattern.matches(line)) return true
    if (trimmed.startsWith(">")) return true
    if (listItemPattern.matches(line)) return true
    if (line.contains('|') && index + 1 < lines.size && isTableDelimiter(lines[index + 1])) return true
    return false
}

private fun parseListItem(line: String): MdListItem? {
    val match = listItemPattern.find(line) ?: return null
    val indent = match.groupValues[1].replace("\t", "    ").length
    val number = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
    var text = match.groupValues[4].trim()
    var checked: Boolean? = null
    taskPattern.find(text)?.let { task ->
        checked = task.groupValues[1] != " "
        text = task.groupValues[2].trim()
    }
    return MdListItem(
        text = text,
        level = (indent / 2).coerceIn(0, 3),
        number = number,
        checked = checked
    )
}

private fun isTableDelimiter(line: String): Boolean {
    if (!line.contains('-')) return false
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { tableCellPattern.matches(it) }
}

private fun splitTableRow(line: String): List<String> {
    var text = line.trim()
    if (text.startsWith("|")) text = text.drop(1)
    if (text.endsWith("|") && !text.endsWith("\\|")) text = text.dropLast(1)
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < text.length) {
        val char = text[i]
        when {
            char == '\\' && i + 1 < text.length && text[i + 1] == '|' -> {
                current.append('|')
                i += 2
            }

            char == '|' -> {
                cells += current.toString().trim()
                current.clear()
                i++
            }

            else -> {
                current.append(char)
                i++
            }
        }
    }
    cells += current.toString().trim()
    return cells
}

/* ------------------------------------------------------------------ */
/*  Встроенная разметка                                                */
/* ------------------------------------------------------------------ */

private val inlinePattern = Regex(
    "`([^`\\n]+)`" +
        "|\\*\\*\\*([^*\\n]+)\\*\\*\\*" +
        "|\\*\\*([^*\\n]+)\\*\\*" +
        "|(?<![\\p{L}\\p{N}_])__([^_\\n]+)__(?![\\p{L}\\p{N}_])" +
        "|~~([^~\\n]+)~~" +
        "|\\*([^*\\n]+)\\*" +
        "|(?<![\\p{L}\\p{N}_])_([^_\\n]+)_(?![\\p{L}\\p{N}_])" +
        "|!?\\[([^\\]\\n]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)" +
        "|(https?://[^\\s<>()\\]]+)"
)

private val inlineCodeStyle = SpanStyle(
    fontFamily = FontFamily.Monospace,
    background = Color(0xFF23201A),
    color = Color(0xFFF0D9B0)
)
private val boldStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = TextMain)
private val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
private val boldItalicStyle = SpanStyle(
    fontWeight = FontWeight.SemiBold,
    fontStyle = FontStyle.Italic,
    color = TextMain
)
private val strikeStyle = SpanStyle(
    textDecoration = TextDecoration.LineThrough,
    color = Muted
)
private val linkStyle = SpanStyle(
    color = Accent,
    textDecoration = TextDecoration.Underline
)

/**
 * Инлайн-разметка: **жирный**, *курсив*, `код`, ~~зачёркнутый~~,
 * [ссылки](url) и голые URL.
 */
internal fun inlineAnnotated(source: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in inlinePattern.findAll(source)) {
        if (match.range.first > last) append(source.substring(last, match.range.first))
        val groups = match.groups
        when {
            groups[1] != null -> withStyle(inlineCodeStyle) { append(groups[1]!!.value) }
            groups[2] != null -> withStyle(boldItalicStyle) { append(groups[2]!!.value) }
            groups[3] != null -> withStyle(boldStyle) { append(groups[3]!!.value) }
            groups[4] != null -> withStyle(boldStyle) { append(groups[4]!!.value) }
            groups[5] != null -> withStyle(strikeStyle) { append(groups[5]!!.value) }
            groups[6] != null -> withStyle(italicStyle) { append(groups[6]!!.value) }
            groups[7] != null -> withStyle(italicStyle) { append(groups[7]!!.value) }
            groups[8] != null -> {
                val label = groups[8]!!.value.ifBlank { groups[9]!!.value }
                val url = groups[9]!!.value
                withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle))) {
                    append(label)
                }
            }

            groups[10] != null -> {
                var url = groups[10]!!.value
                var tail = ""
                while (url.isNotEmpty() && url.last() in ".,;:!?»") {
                    tail = url.last() + tail
                    url = url.dropLast(1)
                }
                withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle))) {
                    append(url)
                }
                if (tail.isNotEmpty()) append(tail)
            }
        }
        last = match.range.last + 1
    }
    if (last < source.length) append(source.substring(last))
}

/** Совместимое имя для абзацев: та же инлайн-разметка. */
internal fun proseAnnotated(source: String): AnnotatedString = inlineAnnotated(source)

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

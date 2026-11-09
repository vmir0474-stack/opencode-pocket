package com.pocket.opencode

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun `parses headings by level`() {
        val blocks = markdownBlocks("# Один\n\n### Три\n#### Четыре")

        assertEquals(3, blocks.size)
        assertEquals(MdBlock.Heading(1, "Один"), blocks[0])
        assertEquals(MdBlock.Heading(3, "Три"), blocks[1])
        assertEquals(MdBlock.Heading(4, "Четыре"), blocks[2])
    }

    @Test
    fun `parses pipe table with alignment`() {
        val source = """
            | Источник | Дата |
            | :--- | ---: |
            | Википедия | 2026 |
            | Meduza | 2017 |
        """.trimIndent()

        val table = markdownBlocks(source).single() as MdBlock.Table

        assertEquals(listOf("Источник", "Дата"), table.header)
        assertEquals(listOf(MdAlign.START, MdAlign.END), table.align)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Википедия", "2026"), table.rows[0])
        assertEquals(listOf("Meduza", "2017"), table.rows[1])
    }

    @Test
    fun `parses bullet ordered and task lists`() {
        val source = """
            - пункт
              - вложенный
            - [x] сделано
            1. первый
            2. второй
        """.trimIndent()

        val blocks = markdownBlocks(source)
        val bullets = blocks[0] as MdBlock.BulletList
        val ordered = blocks[1] as MdBlock.OrderedList

        assertEquals(3, bullets.items.size)
        assertEquals(1, bullets.items[1].level)
        assertEquals(true, bullets.items[2].checked)
        assertEquals(listOf(1, 2), ordered.items.map { it.number })
    }

    @Test
    fun `joins wrapped paragraph lines`() {
        val blocks = markdownBlocks("первая строка\nвторая строка\n\nновый абзац")

        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Paragraph("первая строка вторая строка"), blocks[0])
        assertEquals(MdBlock.Paragraph("новый абзац"), blocks[1])
    }

    @Test
    fun `keeps fenced code intact`() {
        val source = "```kotlin\nval x = 1\n\nprintln(x)\n```"

        val code = markdownBlocks(source).single() as MdBlock.Code

        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1\n\nprintln(x)", code.text)
    }

    @Test
    fun `parses quote and rule`() {
        val blocks = markdownBlocks("> цитата\n> вторая\n\n---")

        assertEquals(MdBlock.Quote(listOf("цитата", "вторая")), blocks[0])
        assertEquals(MdBlock.Rule, blocks[1])
    }

    @Test
    fun `inline markup keeps visible text and spans`() {
        val annotated = inlineAnnotated("**жирный** *курсив* `код` ~~старое~~")
        val text = annotated.text

        assertEquals("жирный курсив код старое", text)
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
        assertTrue(annotated.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(annotated.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(annotated.spanStyles.any { it.item.fontFamily != null })
    }

    @Test
    fun `inline link shows label instead of syntax`() {
        val annotated = inlineAnnotated("источник: [Meduza](https://meduza.io/text)")

        assertEquals("источник: Meduza", annotated.text)
    }

    @Test
    fun `autolink drops trailing punctuation`() {
        val annotated = inlineAnnotated("см. https://example.com/page.")

        assertEquals("см. https://example.com/page.", annotated.text)
    }

    @Test
    fun `underscores inside words are not italics`() {
        val annotated = inlineAnnotated("snake_case_name")

        assertEquals("snake_case_name", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }
}

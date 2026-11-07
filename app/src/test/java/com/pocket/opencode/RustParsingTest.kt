package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RustParsingTest {
    @Test
    fun `mention trigger finds trailing query`() {
        val trigger = mentionTrigger("смотри @Chat")!!
        assertEquals("Chat", trigger.query)
        assertEquals('@', "смотри @Chat"[trigger.at])
        assertEquals(MentionTrigger("", 0), mentionTrigger("@"))
        assertNull(mentionTrigger("email@example.com"))
        assertNull(mentionTrigger("@foo bar"))
        assertNull(mentionTrigger("нет упоминания"))
    }

    @Test
    fun `mention insert replaces the trigger`() {
        assertEquals(
            "прочитай @app/Main.kt ",
            insertMentionRef("прочитай @app/Ma", 9, "app/Main.kt")
        )
        // Символ на позиции не @ — текст не меняется.
        assertEquals("abc", insertMentionRef("abc", 1, "x"))
    }

    @Test
    fun `edit patches parse single edit`() {
        val patches = parseEditPatches(
            "edit",
            """{"filePath":"src/A.kt","oldString":"one","newString":"two"}"""
        )
        assertEquals(1, patches.size)
        assertEquals("src/A.kt", patches[0].path)
        assertEquals("one", patches[0].old)
        assertEquals("two", patches[0].new)
    }

    @Test
    fun `edit patches parse multiedit and unified patch`() {
        val multi = parseEditPatches(
            "multiedit",
            """{"filePath":"src/B.kt","edits":[{"oldString":"a","newString":"b"},{"oldString":"c","newString":"d"}]}"""
        )
        assertEquals(2, multi.size)
        assertTrue(multi.all { it.path == "src/B.kt" })

        val unified = parseEditPatches(
            "apply_patch",
            """{"path":"src/C.kt","patch":"@@ -1,2 +1,2 @@\n-a\n+b\n c"}"""
        )
        assertEquals(1, unified.size)
        assertFalse(unified[0].unified.isNullOrBlank())

        val codexPatch = parseEditPatches(
            "apply_patch",
            """{"patchText":"*** Begin Patch\n*** Update File: src/D.kt\n@@ -1 +1 @@\n-a\n+b\n*** End Patch"}"""
        )
        assertEquals(1, codexPatch.size)
        assertEquals("src/D.kt", codexPatch[0].path)
        assertEquals(
            listOf('h', 'd', 'a'),
            parseUnifiedPatch(codexPatch[0].unified!!).lines.map { it.kind }
        )
    }

    @Test
    fun `non edit tool has no patches`() {
        assertTrue(parseEditPatches("bash", """{"command":"ls"}""").isEmpty())
        assertTrue(parseEditPatches("edit", "not json").isEmpty())
    }

    @Test
    fun `parses rust diff payload`() {
        val diff = parseRustDiff(
            """{"additions":1,"deletions":1,"truncated":false,"lines":[""" +
                """{"k":"h","o":0,"n":0,"t":"@@ -1,1 +1,1 @@"},""" +
                """{"k":"d","o":3,"n":0,"t":"old"},""" +
                """{"k":"a","o":0,"n":3,"t":"new"}]}"""
        )!!
        assertEquals(1, diff.additions)
        assertEquals(1, diff.deletions)
        assertEquals(3, diff.lines.size)
        assertEquals('d', diff.lines[1].kind)
        assertEquals(3, diff.lines[1].oldLine)
        assertEquals("new", diff.lines[2].text)
        assertNull(parseRustDiff("""{"error":"boom"}"""))
    }

    @Test
    fun `parses unified patch lines`() {
        val diff = parseUnifiedPatch(
            """
            --- a/src/A.kt
            +++ b/src/A.kt
            @@ -10,3 +10,3 @@
             context
            -old
            +new
            +extra
            """.trimIndent()
        )
        assertEquals(2, diff.additions)
        assertEquals(1, diff.deletions)
        val kinds = diff.lines.map { it.kind }
        assertEquals(listOf('h', 'c', 'd', 'a', 'a'), kinds)
        assertEquals(10, diff.lines[1].oldLine)
        assertEquals(11, diff.lines[3].newLine)
    }

    @Test
    fun `parses highlight spans`() {
        val lines = parseHighlight(
            """{"lines":[[{"s":0,"e":3,"c":"#cba6f7","b":false,"i":false}]],"truncated":false}"""
        )!!
        assertEquals(1, lines.size)
        assertEquals(1, lines[0].size)
        assertEquals("#cba6f7", lines[0][0].color)
        assertEquals(3, lines[0][0].end)
        assertNull(parseHighlight("""{"error":"no"}"""))
    }

    @Test
    fun `normalizes language aliases`() {
        assertEquals("kt", normalizeLanguage("Kotlin"))
        assertEquals("ts", normalizeLanguage("typescript"))
        assertEquals("sh", normalizeLanguage("bash"))
        assertEquals("yaml", normalizeLanguage("yml"))
        assertEquals("rust", normalizeLanguage("Rust"))
    }

    @Test
    fun `summarizes last turn only`() {
        val messages = listOf(
            UiMessage(
                id = "u1",
                role = UiRole.USER,
                blocks = listOf(UiBlock.Text(content = "old")),
            ),
            UiMessage(
                id = "a1",
                role = UiRole.ASSISTANT,
                blocks = listOf(
                    UiBlock.Tool(callId = "1", name = "read", input = """{"filePath":"old.kt"}"""),
                ),
            ),
            UiMessage(
                id = "u2",
                role = UiRole.USER,
                blocks = listOf(UiBlock.Text(content = "new")),
            ),
            UiMessage(
                id = "a2",
                role = UiRole.ASSISTANT,
                blocks = listOf(
                    UiBlock.Tool(
                        callId = "2",
                        name = "edit",
                        input = """{"filePath":"src/New.kt","oldString":"a","newString":"b"}""",
                        status = "done",
                    ),
                    UiBlock.Tool(
                        callId = "3",
                        name = "bash",
                        input = """{"command":"gradle test"}""",
                        status = "error",
                        error = "failed",
                    ),
                    UiBlock.Tool(
                        callId = "4",
                        name = "grep",
                        input = """{"pattern":"foo"}""",
                        status = "done",
                    ),
                ),
            ),
        )

        val stats = summarizeTurn(messages)!!
        assertEquals(3, stats.tools)
        assertEquals(1, stats.edits)
        assertEquals(1, stats.searches)
        assertEquals(1, stats.errors)
        assertEquals(listOf("src/New.kt"), stats.files)
        assertEquals(listOf("gradle test"), stats.commands)
        assertNull(summarizeTurn(messages.take(1)))
    }

    @Test
    fun `turn report includes files and cost`() {
        val report = turnReport(
            TurnStats(
                tools = 2,
                files = listOf("src/A.kt"),
                commands = listOf("ls"),
                errors = 0,
                edits = 1,
                reads = 0,
                searches = 0,
            ),
            tokensIn = 1200,
            tokensOut = 340,
            cost = 0.0123,
        )
        assertTrue(report.contains("Инструментов: 2"))
        assertTrue(report.contains("· src/A.kt"))
        assertTrue(report.contains("$ ls"))
        assertTrue(report.contains("$0.0123"))
    }
}

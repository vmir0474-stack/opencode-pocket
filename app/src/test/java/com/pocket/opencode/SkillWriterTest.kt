package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkillWriterTest {
    @Test
    fun `renders frontmatter`() {
        assertEquals(
            "---\nname: My Skill\ndescription: Does things\n---\n\n# Body\n",
            SkillWriter.render("My Skill", "Does things", "# Body\n")
        )
    }

    @Test
    fun `safe name lowercases and hyphenates`() {
        assertEquals("my-skill", SkillWriter.safeName(" My Skill! "))
    }

    @Test
    fun `only config and project skills are writable`() {
        val config = File("/app/home/config")
        val ws = File("/sdcard/OpenCode")
        assertTrue(SkillWriter.isWritable("/app/home/config/opencode/skills/a/SKILL.md", config, ws))
        assertTrue(SkillWriter.isWritable("/sdcard/OpenCode/.opencode/skills/a/SKILL.md", config, ws))
        assertFalse(SkillWriter.isWritable("/builtin/opencode.md", config, ws))
    }
}

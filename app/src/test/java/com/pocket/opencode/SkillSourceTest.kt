package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillSourceTest {
    @Test
    fun `parses owner and repo`() {
        assertEquals(
            SkillSource.Repo("anthropics", "skills"),
            SkillSourceParser.parse("anthropics/skills")
        )
    }

    @Test
    fun `parses at shorthand`() {
        assertEquals(
            SkillSource.Repo("anthropics", "skills", null, "pdf"),
            SkillSourceParser.parse("anthropics/skills@pdf")
        )
    }

    @Test
    fun `parses skills sh id as three segments`() {
        assertEquals(
            SkillSource.Repo("anthropics", "skills", null, "pdf"),
            SkillSourceParser.parse("anthropics/skills/pdf")
        )
    }

    @Test
    fun `parses github repo url`() {
        assertEquals(
            SkillSource.Repo("anthropics", "skills"),
            SkillSourceParser.parse("https://github.com/anthropics/skills")
        )
    }

    @Test
    fun `parses github tree url with ref and path`() {
        assertEquals(
            SkillSource.Repo("anthropics", "skills", "main", "skills/pdf"),
            SkillSourceParser.parse("https://github.com/anthropics/skills/tree/main/skills/pdf")
        )
    }

    @Test
    fun `parses github blob url for SKILL md`() {
        assertEquals(
            SkillSource.Repo("anthropics", "skills", "main", "skills/pdf"),
            SkillSourceParser.parse(
                "https://github.com/anthropics/skills/blob/main/skills/pdf/SKILL.md"
            )
        )
    }

    @Test
    fun `parses raw github url`() {
        val source = SkillSourceParser.parse(
            "https://raw.githubusercontent.com/anthropics/skills/main/skills/pdf/SKILL.md"
        )
        assertEquals(
            SkillSource.Raw(
                "https://raw.githubusercontent.com/anthropics/skills/main/skills/pdf/SKILL.md"
            ),
            source
        )
    }

    @Test
    fun `parses skills sh url`() {
        assertEquals(
            SkillSource.SkillsSh("anthropics", "skills", "pdf"),
            SkillSourceParser.parse("https://skills.sh/anthropics/skills/pdf")
        )
    }

    @Test
    fun `rejects unsafe or empty input`() {
        assertNull(SkillSourceParser.parse(""))
        assertNull(SkillSourceParser.parse("   "))
        assertNull(SkillSourceParser.parse("javascript:alert(1)"))
        assertNull(SkillSourceParser.parse("file:///etc/passwd"))
        assertNull(SkillSourceParser.parse("not a repo"))
    }

    @Test
    fun `label is readable`() {
        assertEquals("anthropics/skills@pdf", SkillSource.Repo("anthropics", "skills", null, "pdf").label)
        assertEquals("anthropics/skills/pdf", SkillSource.SkillsSh("anthropics", "skills", "pdf").label)
    }
}

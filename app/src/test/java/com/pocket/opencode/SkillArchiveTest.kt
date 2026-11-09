package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class SkillArchiveTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `safe paths reject traversal and absolute paths`() {
        assertTrue(SkillArchive.safePath("repo-main/skills/pdf/SKILL.md"))
        assertFalse(SkillArchive.safePath("../evil"))
        assertFalse(SkillArchive.safePath("repo-main/../../evil"))
        assertFalse(SkillArchive.safePath("/etc/passwd"))
        assertFalse(SkillArchive.safePath("repo-main\\evil"))
        assertFalse(SkillArchive.safePath("repo-main//evil"))
        assertFalse(SkillArchive.safePath(""))
    }

    @Test
    fun `skill dirs are found and filtered`() {
        val entries = listOf(
            SkillEntry("repo-main/skills/pdf/SKILL.md", false, 10),
            SkillEntry("repo-main/skills/docx/SKILL.md", false, 10),
            SkillEntry("repo-main/README.md", false, 10),
        )
        assertEquals(
            listOf("repo-main/skills/pdf", "repo-main/skills/docx"),
            SkillArchive.allSkillDirs(entries)
        )
        assertEquals(
            listOf("repo-main/skills/pdf"),
            SkillArchive.skillDirs(entries, "pdf")
        )
        assertEquals(
            listOf("repo-main/skills/pdf"),
            SkillArchive.skillDirs(entries, "skills/pdf")
        )
        assertTrue(SkillArchive.skillDirs(entries, "missing").isEmpty())
    }

    @Test
    fun `extract writes only selected dirs and skips symlinks`() {
        val archive = tarGz(
            files = mapOf(
                "repo-main/skills/pdf/SKILL.md" to "# pdf",
                "repo-main/skills/pdf/scripts/run.py" to "print(1)",
                "repo-main/skills/docx/SKILL.md" to "# docx",
                "repo-main/README.md" to "readme",
            ),
            symlinks = mapOf("repo-main/skills/pdf/link" to "/etc/passwd"),
        )
        val dest = temp.newFolder("out")
        val count = SkillArchive.extract(
            ByteArrayInputStream(archive),
            dest,
            setOf("repo-main/skills/pdf"),
        )
        assertEquals(2, count)
        assertTrue(File(dest, "repo-main/skills/pdf/SKILL.md").isFile)
        assertTrue(File(dest, "repo-main/skills/pdf/scripts/run.py").isFile)
        assertFalse(File(dest, "repo-main/skills/docx/SKILL.md").exists())
        assertFalse(File(dest, "repo-main/README.md").exists())
        assertFalse(File(dest, "repo-main/skills/pdf/link").exists())
    }

    @Test
    fun `extract skips traversal entries`() {
        val archive = tarGz(
            files = mapOf(
                "repo-main/skills/pdf/SKILL.md" to "# pdf",
                "repo-main/skills/pdf/../../evil.txt" to "evil",
            )
        )
        val dest = temp.newFolder("out2")
        SkillArchive.extract(
            ByteArrayInputStream(archive),
            dest,
            setOf("repo-main/skills/pdf"),
        )
        assertFalse(File(dest.parentFile, "evil.txt").exists())
        assertFalse(File(dest, "evil.txt").exists())
    }

    @Test(expected = IllegalStateException::class)
    fun `extract enforces limits`() {
        val archive = tarGz(
            files = mapOf(
                "repo-main/skills/pdf/SKILL.md" to "# pdf",
                "repo-main/skills/pdf/a.txt" to "a",
                "repo-main/skills/pdf/b.txt" to "b",
            )
        )
        val dest = temp.newFolder("out3")
        SkillArchive.extract(
            ByteArrayInputStream(archive),
            dest,
            setOf("repo-main/skills/pdf"),
            SkillLimits(maxFiles = 1),
        )
    }
}

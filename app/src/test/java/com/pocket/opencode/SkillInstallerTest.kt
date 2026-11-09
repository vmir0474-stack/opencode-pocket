package com.pocket.opencode

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SkillInstallerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun paths(): ServerPaths {
        val root = temp.newFolder("app")
        val workspace = temp.newFolder("workspace")
        return ServerPaths.under(root, workspace)
    }

    private fun repoFetcher(): FakeSkillFetcher {
        val archive = tarGz(
            mapOf(
                "skills-main/skills/pdf/SKILL.md" to
                    "---\nname: pdf\ndescription: Work with PDF\n---\n\n# PDF\n",
                "skills-main/skills/pdf/scripts/run.py" to "print(1)\n",
                "skills-main/skills/docx/SKILL.md" to
                    "---\nname: docx\ndescription: Work with DOCX\n---\n\n# DOCX\n",
            )
        )
        return FakeSkillFetcher(
            mapOf(
                "https://api.github.com/repos/anthropics/skills" to
                    """{"default_branch":"main"}""".toByteArray(),
                "https://codeload.github.com/anthropics/skills/tar.gz/main" to archive,
            )
        )
    }

    @Test
    fun `installs only the requested skill from a multi-skill repo`() = runBlocking {
        val paths = paths()
        val installed = SkillInstaller(repoFetcher()).install("anthropics/skills@pdf", paths)

        assertEquals(1, installed.size)
        assertEquals("pdf", installed.first().name)
        assertTrue(File(paths.config, "opencode/skills/pdf/SKILL.md").isFile)
        assertTrue(File(paths.config, "opencode/skills/pdf/scripts/run.py").isFile)
        assertFalse(File(paths.config, "opencode/skills/docx").exists())
        assertTrue(File(paths.config, "opencode/skills/pdf/.pocket-skill.json").isFile)
    }

    @Test
    fun `finds a skill by frontmatter name when the directory differs`() = runBlocking {
        val archive = tarGz(
            mapOf(
                "repo-main/tools/pdf-tools/SKILL.md" to
                    "---\nname: pdf\ndescription: Work with PDF\n---\n",
            )
        )
        val fetcher = FakeSkillFetcher(
            mapOf(
                "https://api.github.com/repos/acme/tools" to
                    """{"default_branch":"main"}""".toByteArray(),
                "https://codeload.github.com/acme/tools/tar.gz/main" to archive,
            )
        )
        val installed = SkillInstaller(fetcher).install("acme/tools@pdf", paths())
        assertEquals(1, installed.size)
        assertEquals("pdf", installed.first().name)
    }

    @Test
    fun `refuses to overwrite without the flag`() = runBlocking {
        val paths = paths()
        val installer = SkillInstaller(repoFetcher())
        installer.install("anthropics/skills@pdf", paths)
        try {
            installer.install("anthropics/skills@pdf", paths)
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("уже установлен"))
        }
    }

    @Test
    fun `overwrite replaces the installed copy`() = runBlocking {
        val paths = paths()
        SkillInstaller(repoFetcher()).install("anthropics/skills@pdf", paths)

        val changed = tarGz(
            mapOf(
                "skills-main/skills/pdf/SKILL.md" to
                    "---\nname: pdf\ndescription: Work with PDF v2\n---\n\n# v2\n",
            )
        )
        val fetcher = FakeSkillFetcher(
            mapOf(
                "https://api.github.com/repos/anthropics/skills" to
                    """{"default_branch":"main"}""".toByteArray(),
                "https://codeload.github.com/anthropics/skills/tar.gz/main" to changed,
            )
        )
        SkillInstaller(fetcher).install("anthropics/skills@pdf", paths, overwrite = true)
        val text = File(paths.config, "opencode/skills/pdf/SKILL.md").readText()
        assertTrue(text.contains("v2"))
    }

    @Test
    fun `installs a raw SKILL md`() = runBlocking {
        val paths = paths()
        val url = "https://raw.githubusercontent.com/acme/one/main/SKILL.md"
        val fetcher = FakeSkillFetcher(
            mapOf(
                url to "---\nname: Raw Skill\ndescription: From a raw URL\n---\n\n# Raw\n"
                    .toByteArray()
            )
        )
        val installed = SkillInstaller(fetcher).install(url, paths)
        assertEquals("Raw Skill", installed.first().name)
        assertTrue(File(paths.config, "opencode/skills/raw-skill/SKILL.md").isFile)
    }

    @Test
    fun `rejects raw files without frontmatter`() = runBlocking {
        val paths = paths()
        val url = "https://raw.githubusercontent.com/acme/one/main/SKILL.md"
        val fetcher = FakeSkillFetcher(mapOf(url to "# no frontmatter\n".toByteArray()))
        try {
            SkillInstaller(fetcher).install(url, paths)
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("name"))
        }
        assertFalse(File(paths.config, "opencode/skills/no-frontmatter").exists())
    }

    @Test
    fun `lists updates and removes installed skills`() = runBlocking {
        val paths = paths()
        val installer = SkillInstaller(repoFetcher())
        installer.install("anthropics/skills@pdf", paths)

        val listed = installer.list(paths)
        assertEquals(1, listed.size)
        assertEquals("anthropics/skills@pdf", listed.first().source)

        val updated = installer.update(paths, "pdf", all = false)
        assertEquals(1, updated.size)

        assertTrue(installer.remove(paths, "pdf"))
        assertFalse(File(paths.config, "opencode/skills/pdf").exists())
    }

    @Test
    fun `refuses to remove a skill that was not installed by pocket-skill`() {
        val paths = paths()
        val dir = File(paths.config, "opencode/skills/manual")
        dir.mkdirs()
        File(dir, "SKILL.md").writeText("---\nname: manual\ndescription: x\n---\n")
        try {
            SkillInstaller(repoFetcher()).remove(paths, "manual")
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("вручную"))
        }
    }

    @Test
    fun `installs into the project when asked`() = runBlocking {
        val paths = paths()
        SkillInstaller(repoFetcher()).install("anthropics/skills@pdf", paths, project = true)
        assertTrue(File(paths.workspace, ".opencode/skills/pdf/SKILL.md").isFile)
        assertFalse(File(paths.config, "opencode/skills/pdf").exists())
    }
}

package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillSeedPolicyTest {
    @Test
    fun neededKeepsOnlyMissingFiles() {
        val assets = setOf("a/SKILL.md", "a/run.sh", "b/SKILL.md")
        val existing = setOf("a/SKILL.md", "b/SKILL.md")

        assertEquals(setOf("a/run.sh"), SkillSeedPolicy.needed(assets, existing))
    }

    @Test
    fun safeRelativeAcceptsNestedPath() {
        assertEquals("a/scripts/run.sh", SkillSeedPolicy.safeRelative("./a/scripts/run.sh"))
    }

    @Test
    fun safeRelativeRejectsTraversalAndAbsoluteParents() {
        assertNull(SkillSeedPolicy.safeRelative("../secret"))
        assertNull(SkillSeedPolicy.safeRelative("a/../../secret"))
        assertNull(SkillSeedPolicy.safeRelative("/"))
        assertNull(SkillSeedPolicy.safeRelative("a//b"))
    }

    @Test
    fun targetRelativeRestoresAssetWorkarounds() {
        assertEquals(
            "_sources/ATTRIBUTION.md",
            SkillSeedPolicy.targetRelative("skill-sources/ATTRIBUTION.md")
        )
        assertEquals(
            "web-artifacts-builder/scripts/shadcn-components.tar.gz",
            SkillSeedPolicy.targetRelative(
                "web-artifacts-builder/scripts/shadcn-components.tar.gz.asset"
            )
        )
        assertEquals("brainstorming/SKILL.md", SkillSeedPolicy.targetRelative("brainstorming/SKILL.md"))
        assertNull(SkillSeedPolicy.targetRelative("../secret"))
    }
}

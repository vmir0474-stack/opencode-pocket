package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    private fun p(id: String, connected: Boolean = false) =
        IntegrationInfo(
            id,
            id,
            emptyList(),
            if (connected) {
                listOf(IntegrationConnection("credential", "c", null, "key"))
            } else {
                emptyList()
            }
        )

    @Test
    fun `connected providers come first`() {
        val out = ProviderCatalog.ordered(listOf(p("zzz"), p("openai", true), p("anthropic")))
        assertEquals(listOf("openai", "anthropic", "zzz"), out.map { it.id })
    }

    @Test
    fun `search matches name and id`() {
        assertTrue(ProviderCatalog.matches(p("openrouter"), "open"))
        assertFalse(ProviderCatalog.matches(p("openrouter"), "zzz"))
    }
}

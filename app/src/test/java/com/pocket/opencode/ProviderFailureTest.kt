package com.pocket.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFailureTest {
    @Test
    fun `typed free limit is quota`() {
        assertEquals(
            ProviderFailure.QUOTA,
            providerFailure("provider.free-limit", "Free usage limit reached.")
        )
    }

    @Test
    fun `raw 429 text is quota even without a type`() {
        assertEquals(
            ProviderFailure.QUOTA,
            providerFailure(null, "AI_APICallError: Free usage limit reached. Free models work in OpenCode")
        )
        assertEquals(
            ProviderFailure.QUOTA,
            providerFailure(null, "Free usage exceeded, subscribe to Go")
        )
    }

    @Test
    fun `abusive traffic is a network block, not quota`() {
        assertEquals(
            ProviderFailure.NETWORK_BLOCK,
            providerFailure(
                null,
                "OpenCode's free models aren't available from your current network because " +
                    "we've detected abusive traffic from this IP address. This isn't related to your account."
            )
        )
    }

    @Test
    fun `region block is its own kind`() {
        assertEquals(
            ProviderFailure.REGION,
            providerFailure(null, "This model is not available in your region.")
        )
    }

    @Test
    fun `auth errors are not mistaken for quota`() {
        assertEquals(ProviderFailure.AUTH, providerFailure(null, "401 Unauthorized"))
        assertEquals(ProviderFailure.AUTH, providerFailure(null, "Invalid API key provided"))
    }

    @Test
    fun `unknown errors keep the raw text`() {
        assertEquals(ProviderFailure.OTHER, providerFailure(null, "connection reset by peer"))
        assertEquals("connection reset by peer", failureText(ProviderFailure.OTHER, "connection reset by peer"))
    }

    @Test
    fun `each actionable failure gets a distinct text`() {
        val texts = listOf(
            failureText(ProviderFailure.QUOTA, "x", 0L),
            failureText(ProviderFailure.NETWORK_BLOCK, "x"),
            failureText(ProviderFailure.REGION, "x"),
            failureText(ProviderFailure.AUTH, "x")
        )
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test
    fun `network block text says the account is not the cause`() {
        val text = failureText(ProviderFailure.NETWORK_BLOCK, "x")
        assertTrue(text.contains("сетевого подключения"))
        assertTrue(text.contains("не связано с аккаунтом"))
        assertTrue(!text.contains("VPN"))
        assertTrue(!text.contains("адрес"))
    }

    @Test
    fun `quota text does not promise that signing in helps`() {
        val text = failureText(ProviderFailure.QUOTA, "x", 0L)
        assertTrue(!text.contains("Войти"))
        assertTrue(text.contains("ключ"))
    }
}

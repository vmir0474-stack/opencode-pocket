package com.pocket.opencode

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeLimitMessageTest {
    @Test
    fun `assistant message keeps provider error type`() {
        val data = JSONArray().put(
            JSONObject()
                .put("id", "msg_1")
                .put("type", "assistant")
                .put("time", JSONObject().put("created", 123L))
                .put("content", JSONArray())
                .put(
                    "error",
                    JSONObject()
                        .put("type", "provider.free-limit")
                        .put("message", "Free usage limit reached.")
                )
        )
        val message = parseServerMessages(data).single()
        assertEquals("provider.free-limit", message.errorType)
        assertEquals("Free usage limit reached.", message.error)
    }

    @Test
    fun `free limit text points at UTC reset and provider key`() {
        val oneHourAfterMidnight = 86_400_000L * 20_000L + 3_600_000L
        val text = freeLimitText(oneHourAfterMidnight)
        assertTrue(text.contains("не привязан к IP"))
        assertTrue(text.contains("00:00 UTC"))
        assertTrue(text.contains("23 ч 0 мин"))
        assertTrue(text.contains("Модели"))
    }

    @Test
    fun `oauth attempt parses device url and code`() {
        val attempt = parseOAuthAttempt(
            JSONObject()
                .put("attemptID", "con_1")
                .put(
                    "url",
                    "https://opencode.ai/console/device?user_code=ABCD-EFGH&client_id=opencode-cli"
                )
                .put("instructions", "Enter code: ABCD-EFGH")
                .put("mode", "auto")
                .put("time", JSONObject().put("created", 1L).put("expires", 2L))
        )
        assertEquals("con_1", attempt.id)
        assertTrue(attempt.url.contains("user_code=ABCD-EFGH"))
        assertEquals("Enter code: ABCD-EFGH", attempt.instructions)
        assertEquals(2L, attempt.expires)
    }

    @Test
    fun `error type survives the local cache`() {
        val message = UiMessage(
            id = "msg_1",
            role = UiRole.ASSISTANT,
            error = "Free usage limit reached.",
            errorType = "provider.free-limit"
        )
        val restored = cacheFromJson(
            JSONObject().put("messages", JSONArray().put(message.toCacheJson())).toString()
        ).single()
        assertEquals("provider.free-limit", restored.errorType)
    }
}

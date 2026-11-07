package com.pocket.opencode

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionModelTest {
    @Test
    fun `model capabilities expose image support`() {
        val vision = parseModel(
            JSONObject(
                """
                {
                  "modelID": "vl-free",
                  "providerID": "opencode",
                  "name": "VL Free",
                  "capabilities": { "input": ["text", "image"] }
                }
                """.trimIndent()
            )
        )
        assertTrue(vision.supportsImages)

        val text = parseModel(
            JSONObject(
                """
                {
                  "modelID": "big-pickle",
                  "providerID": "opencode",
                  "capabilities": { "input": ["text"] }
                }
                """.trimIndent()
            )
        )
        assertFalse(text.supportsImages)
    }

    @Test
    fun `image detection uses mime extension and data uri`() {
        assertTrue(isImageAttachment("file:///tmp/a.png", "", "a.png"))
        assertTrue(isImageAttachment("file:///tmp/a", "image/webp", "a"))
        assertTrue(isImageAttachment("data:image/png;base64,AAAA", "", ""))
        assertFalse(isImageAttachment("file:///tmp/a.txt", "text/plain", "a.txt"))
    }

    @Test
    fun `image blocks survive cache roundtrip`() {
        val message = UiMessage(
            id = "m1",
            role = UiRole.USER,
            blocks = listOf(
                UiBlock.Text(content = "смотри"),
                UiBlock.Image(uri = "file:///data/a.jpg", name = "a.jpg")
            )
        )
        val raw = JSONObject()
        val arr = org.json.JSONArray().put(message.toCacheJson())
        raw.put("messages", arr)
        val restored = cacheFromJson(raw.toString())
        val blocks = restored.single().blocks
        assertEquals(2, blocks.size)
        val image = blocks[1] as UiBlock.Image
        assertEquals("file:///data/a.jpg", image.uri)
        assertEquals("a.jpg", image.name)
    }

    @Test
    fun `mcp status is parsed`() {
        val info = parseMcp(
            JSONObject(
                """{"name":"context7","title":"Context7","status":{"status":"connected"}}"""
            )
        )
        assertTrue(info.connected)
        assertEquals("Context7", info.title)

        val failed = parseMcp(
            JSONObject(
                """{"name":"x","status":{"status":"failed","error":"boom"}}"""
            )
        )
        assertFalse(failed.connected)
        assertEquals("boom", failed.error)
    }
}

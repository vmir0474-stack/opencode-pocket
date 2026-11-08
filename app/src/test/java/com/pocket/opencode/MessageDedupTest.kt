package com.pocket.opencode

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDedupTest {
    @Test
    fun `last message wins for duplicate ids`() {
        val arr = JSONArray()
            .put(JSONObject().put("id", "msg_1").put("type", "user").put("text", "old"))
            .put(JSONObject().put("id", "msg_1").put("type", "user").put("text", "new"))
        val out = parseServerMessages(arr)
        assertEquals(1, out.size)
        assertEquals("new", (out[0].blocks.single() as UiBlock.Text).content)
    }

    @Test
    fun `newest first page is restored to chronological order`() {
        val arr = JSONArray()
            .put(JSONObject().put("id", "msg_3").put("type", "user").put("text", "third"))
            .put(JSONObject().put("id", "msg_2").put("type", "user").put("text", "second"))
            .put(JSONObject().put("id", "msg_1").put("type", "user").put("text", "first"))
        val out = parseServerMessagesNewestFirst(arr)
        assertEquals(listOf("msg_1", "msg_2", "msg_3"), out.map { it.id })
    }

    @Test
    fun `newest duplicate wins after reordering`() {
        val arr = JSONArray()
            .put(JSONObject().put("id", "msg_1").put("type", "user").put("text", "new"))
            .put(JSONObject().put("id", "msg_1").put("type", "user").put("text", "old"))
        val out = parseServerMessagesNewestFirst(arr)
        assertEquals(1, out.size)
        assertEquals("new", (out[0].blocks.single() as UiBlock.Text).content)
    }
}

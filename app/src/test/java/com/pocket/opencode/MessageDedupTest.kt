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
}

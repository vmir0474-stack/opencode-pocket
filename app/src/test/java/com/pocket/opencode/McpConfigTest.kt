package com.pocket.opencode

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class McpConfigTest {
    @Test
    fun `adds remote server and keeps existing keys`() {
        val merged = McpConfig.merge("""{"model":"big-pickle"}""", "context7", "https://mcp.context7.com/mcp")
        val root = JSONObject(merged!!)
        assertEquals("big-pickle", root.getString("model"))
        val server = root.getJSONObject("mcp").getJSONObject("servers").getJSONObject("context7")
        assertEquals("remote", server.getString("type"))
        assertEquals("https://mcp.context7.com/mcp", server.getString("url"))
    }

    @Test
    fun `merges into existing servers block`() {
        val source =
            """{"mcp":{"servers":{"deepwiki":{"type":"remote","url":"https://a"}}}}"""
        val root = JSONObject(McpConfig.merge(source, "context7", "https://b")!!)
        val servers = root.getJSONObject("mcp").getJSONObject("servers")
        assertEquals(2, servers.length())
        assertEquals("https://a", servers.getJSONObject("deepwiki").getString("url"))
    }

    @Test
    fun `keeps unrelated mcp keys`() {
        val source = """{"mcp":{"timeout":30}}"""
        val root = JSONObject(McpConfig.merge(source, "x", "https://x")!!)
        assertEquals(30, root.getJSONObject("mcp").getInt("timeout"))
    }

    @Test
    fun `removes server and drops empty blocks`() {
        val source =
            """{"mcp":{"servers":{"context7":{"type":"remote","url":"https://a"}}}}"""
        val root = JSONObject(McpConfig.remove(source, "context7")!!)
        assertFalse(root.has("mcp"))
    }

    @Test
    fun `broken config is left untouched`() {
        assertNull(McpConfig.merge("{ // jsonc", "x", "https://x"))
        assertNull(McpConfig.remove("not json", "x"))
    }

    @Test
    fun `reads persisted url`() {
        val source =
            """{"mcp":{"servers":{"context7":{"type":"remote","url":"https://a"}}}}"""
        assertEquals("https://a", McpConfig.remoteUrl(source, "context7"))
        assertNull(McpConfig.remoteUrl(source, "other"))
    }

    @Test
    fun `old flat layout is not misread`() {
        val source = """{"mcp":{"context7":{"type":"remote","url":"https://a"}}}"""
        assertNull(McpConfig.remoteUrl(source, "context7"))
    }
}

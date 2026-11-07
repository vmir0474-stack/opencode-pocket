package com.pocket.opencode

import org.json.JSONObject

/**
 * MCP-серверы, добавленные через runtime API, живут только до перезапуска
 * процесса. Чтобы они пережили рестарт, дублируем их в opencode.json —
 * OpenCode v2 читает ключ `mcp.servers` при старте.
 */
object McpConfig {
    fun merge(text: String, name: String, url: String): String? {
        val root = parse(text) ?: return null
        val mcp = root.optJSONObject("mcp") ?: JSONObject().also { root.put("mcp", it) }
        val servers = mcp.optJSONObject("servers") ?: JSONObject().also { mcp.put("servers", it) }
        servers.put(
            name,
            JSONObject()
                .put("type", "remote")
                .put("url", url)
        )
        return root.toString(2)
    }

    fun remove(text: String, name: String): String? {
        val root = parse(text) ?: return null
        val mcp = root.optJSONObject("mcp") ?: return root.toString(2)
        val servers = mcp.optJSONObject("servers") ?: return root.toString(2)
        servers.remove(name)
        if (servers.length() == 0) mcp.remove("servers")
        if (mcp.length() == 0) root.remove("mcp")
        return root.toString(2)
    }

    fun remoteUrl(text: String, name: String): String? {
        val root = parse(text) ?: return null
        return root.optJSONObject("mcp")
            ?.optJSONObject("servers")
            ?.optJSONObject(name)
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }
    }

    private fun parse(text: String): JSONObject? = runCatching {
        if (text.isBlank()) JSONObject() else JSONObject(text)
    }.getOrNull()
}

package com.pocket.opencode

import org.json.JSONObject

data class IntegrationMethod(
    val type: String,
    val id: String = "",
    val label: String? = null,
    val names: List<String> = emptyList()
)

data class IntegrationConnection(
    val type: String,
    val id: String,
    val label: String?,
    val method: String
)

data class IntegrationInfo(
    val id: String,
    val name: String,
    val methods: List<IntegrationMethod>,
    val connections: List<IntegrationConnection>
) {
    val supportsKey: Boolean get() = methods.any { it.type == "key" }
    val supportsOAuth: Boolean get() = methods.any { it.type == "oauth" }
    val oauthMethodID: String?
        get() = methods.firstOrNull { it.type == "oauth" && it.id.isNotBlank() }?.id
    val connected: Boolean get() = connections.isNotEmpty()
}

/** Device-code login started by the embedded server. */
data class OAuthAttempt(
    val id: String,
    val url: String,
    val instructions: String,
    val mode: String,
    val expires: Long
)

data class OAuthStatus(
    val status: String,
    val message: String? = null
)

fun parseOAuthAttempt(raw: JSONObject): OAuthAttempt = OAuthAttempt(
    id = raw.optString("attemptID"),
    url = raw.optString("url"),
    instructions = raw.optString("instructions"),
    mode = raw.optString("mode", "auto"),
    expires = raw.optJSONObject("time")?.optLong("expires") ?: 0L
)

fun parseOAuthStatus(raw: JSONObject): OAuthStatus = OAuthStatus(
    status = raw.optString("status"),
    message = raw.optText("message")
)

data class CredentialInfo(
    val id: String,
    val integrationID: String,
    val label: String,
    val active: Boolean
)

data class SkillInfo(
    val id: String,
    val name: String,
    val description: String,
    val path: String,
    val content: String
)

fun parseIntegration(raw: JSONObject): IntegrationInfo {
    val methods = mutableListOf<IntegrationMethod>()
    raw.optJSONArray("methods")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val names = mutableListOf<String>()
            o.optJSONArray("names")?.let { list ->
                for (n in 0 until list.length()) {
                    list.optString(n).takeIf { it.isNotBlank() }?.let { names += it }
                }
            }
            methods += IntegrationMethod(
                type = o.optString("type"),
                id = o.optString("id"),
                label = o.optString("label").takeIf { it.isNotBlank() },
                names = names
            )
        }
    }
    val connections = mutableListOf<IntegrationConnection>()
    raw.optJSONArray("connections")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("type").ifBlank { "credential" }
            connections += IntegrationConnection(
                type = type,
                id = o.optString("id").ifBlank { o.optString("credentialID") },
                label = o.optString("label").takeIf { it.isNotBlank() },
                method = o.optString("method").ifBlank { type }
            )
        }
    }
    return IntegrationInfo(
        id = raw.optString("id"),
        name = raw.optString("name").ifBlank { raw.optString("id") },
        methods = methods,
        connections = connections
    )
}

fun parseCredential(raw: JSONObject): CredentialInfo = CredentialInfo(
    id = raw.optString("id"),
    integrationID = raw.optString("integrationID").ifBlank { raw.optString("integrationId") },
    label = raw.optString("label").ifBlank { "default" },
    active = raw.optBoolean("active", true)
)

fun parseSkill(raw: JSONObject): SkillInfo = SkillInfo(
    id = raw.optString("id"),
    name = raw.optString("name").ifBlank { raw.optString("id") },
    description = raw.optString("description"),
    path = raw.optString("path"),
    content = raw.optString("content")
)

/** Тело POST /api/credential — ровно та схема, которую ждёт сервер. */
object CredentialPayload {
    fun key(integrationID: String, key: String, label: String = "default"): JSONObject =
        JSONObject().apply {
            put("integrationID", integrationID)
            put("label", label)
            put("value", JSONObject().put("type", "key").put("key", key))
        }
}

object ProviderCatalog {
    val curated = listOf(
        "opencode", "anthropic", "openai", "openrouter", "google",
        "deepseek", "xai", "groq", "mistral"
    )

    fun ordered(list: List<IntegrationInfo>): List<IntegrationInfo> =
        list.sortedWith(
            compareBy<IntegrationInfo>(
                { if (it.connected) 0 else 1 },
                { curated.indexOf(it.id).let { index -> if (index < 0) curated.size else index } },
                { it.name.lowercase() }
            )
        )

    fun matches(item: IntegrationInfo, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return item.id.lowercase().contains(q) || item.name.lowercase().contains(q)
    }
}
